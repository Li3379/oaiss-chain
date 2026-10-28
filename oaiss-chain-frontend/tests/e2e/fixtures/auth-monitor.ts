import { type Page } from '@playwright/test'

/**
 * Auth event types detected by the monitor
 */
export type AuthEventType =
  | 'token_expired'          // HTTP 401 or backend code 2000
  | 'permission_denied'      // HTTP 403 or "没有权限" message
  | 'unexpected_login_redirect'  // Redirected to /login unexpectedly
  | 'token_refresh_failed'   // Refresh token failure
  | 'session_lost'           // Token disappeared from storage

/**
 * A single auth-related event captured by the monitor
 */
export interface AuthEvent {
  type: AuthEventType
  timestamp: number
  url?: string
  statusCode?: number
  backendCode?: number
  message?: string
}

/**
 * Severity levels for auth events
 */
export type Severity = 'info' | 'warning' | 'critical'

/**
 * Monitor configuration
 */
export interface AuthMonitorOptions {
  /** Callback invoked when an auth issue is detected */
  onAuthIssue?: (event: AuthEvent) => Promise<void>
  /** Whether to auto-detect ElMessage toast notifications (default: true) */
  watchToasts?: boolean
  /** Whether to monitor route changes for unexpected /login redirects (default: true) */
  watchRoutes?: boolean
  /** Whether to monitor API responses for 401/403/code=2000 (default: true) */
  watchApiResponses?: boolean
  /** Expected page URL patterns that indicate login redirect is intentional */
  loginPages?: string[]
}

/**
 * Final monitoring report
 */
export interface AuthReport {
  events: AuthEvent[]
  summary: {
    total: number
    byType: Record<AuthEventType, number>
    maxSeverity: Severity
  }
  duration: number
}

/**
 * AuthMonitor — Real-time authentication health monitor for E2E tests.
 *
 * Attaches to a Playwright page and watches for auth-related issues:
 * - API responses with 401/403 status or backend error code 2000
 * - Unexpected redirects to /login
 * - Element Plus error toast messages ("登录已过期", "没有权限", etc.)
 * - Token disappearing from sessionStorage
 *
 * Usage:
 * ```ts
 * const monitor = new AuthMonitor(page, {
 *   onAuthIssue: async (event) => { console.warn('Auth issue:', event) }
 * })
 * monitor.start()
 * // ... run test ...
 * const report = monitor.stop()
 * ```
 */
export class AuthMonitor {
  private page: Page
  private options: Required<AuthMonitorOptions>
  private events: AuthEvent[] = []
  private startTime = 0
  private cleanupFns: (() => void)[] = []
  private running = false

  constructor(page: Page, options?: AuthMonitorOptions) {
    this.page = page
    this.options = {
      onAuthIssue: options?.onAuthIssue ?? (async () => {}),
      watchToasts: options?.watchToasts ?? true,
      watchRoutes: options?.watchRoutes ?? true,
      watchApiResponses: options?.watchApiResponses ?? true,
      loginPages: options?.loginPages ?? ['/login', '/register'],
    }
  }

  /** Start monitoring */
  start(): void {
    if (this.running) return
    this.running = true
    this.startTime = Date.now()
    this.events = []

    if (this.options.watchApiResponses) {
      this.watchApiResponses()
    }
    if (this.options.watchRoutes) {
      this.watchRouteChanges()
    }
    if (this.options.watchToasts) {
      this.watchToastMessages()
    }
  }

  /** Stop monitoring and return the report */
  stop(): AuthReport {
    this.running = false
    this.cleanupFns.forEach(fn => fn())
    this.cleanupFns = []

    const duration = Date.now() - this.startTime
    const byType: Record<string, number> = {}
    for (const event of this.events) {
      byType[event.type] = (byType[event.type] || 0) + 1
    }

    const maxSeverity: Severity = this.events.some(e =>
      e.type === 'token_expired' || e.type === 'token_refresh_failed' || e.type === 'session_lost'
    ) ? 'critical'
      : this.events.some(e => e.type === 'permission_denied') ? 'warning'
      : 'info'

    return {
      events: [...this.events],
      summary: {
        total: this.events.length,
        byType: byType as Record<AuthEventType, number>,
        maxSeverity,
      },
      duration,
    }
  }

  /** Get events collected so far without stopping */
  getEvents(): AuthEvent[] {
    return [...this.events]
  }

  /** Check if any critical auth issues have been detected */
  hasIssues(): boolean {
    return this.events.length > 0
  }

  /** Get the last captured event */
  getLastEvent(): AuthEvent | undefined {
    return this.events[this.events.length - 1]
  }

  private recordEvent(event: AuthEvent): void {
    this.events.push(event)
    if (this.options.onAuthIssue) {
      this.options.onAuthIssue(event).catch(() => {})
    }
  }

  /** Watch API responses for auth-related error codes */
  private watchApiResponses(): void {
    const handler = async (response: import('@playwright/test').Response) => {
      if (!this.running) return
      const url = response.url()
      const status = response.status()

      // Only monitor API calls
      if (!url.includes('/api/')) return

      if (status === 401) {
        let message = '登录已过期，请重新登录'
        try {
          const body = await response.json().catch(() => null)
          message = body?.message || message
          const backendCode = body?.code
          this.recordEvent({
            type: 'token_expired',
            timestamp: Date.now(),
            url,
            statusCode: status,
            backendCode,
            message,
          })
        } catch {
          this.recordEvent({
            type: 'token_expired',
            timestamp: Date.now(),
            url,
            statusCode: status,
            message,
          })
        }
      } else if (status === 403) {
        this.recordEvent({
          type: 'permission_denied',
          timestamp: Date.now(),
          url,
          statusCode: status,
          message: '没有权限执行此操作',
        })
      } else {
        // Check backend custom error codes in response body
        try {
          const body = await response.json().catch(() => null)
          if (body && typeof body === 'object') {
            const code = body.code
            if (code === 2000) {
              this.recordEvent({
                type: 'token_expired',
                timestamp: Date.now(),
                url,
                statusCode: status,
                backendCode: code,
                message: body.message || '用户未登录或Token已过期',
              })
            } else if (code === 1003) {
              this.recordEvent({
                type: 'permission_denied',
                timestamp: Date.now(),
                url,
                statusCode: status,
                backendCode: code,
                message: body.message || 'IP不在白名单中',
              })
            }
          }
        } catch {
          // Response body not JSON — ignore
        }
      }
    }

    this.page.on('response', handler)
    this.cleanupFns.push(() => this.page.off('response', handler))
  }

  /** Watch for unexpected redirects to /login */
  private watchRouteChanges(): void {
    const handler = (frame: import('@playwright/test').Frame) => {
      if (!this.running) return
      const url = frame.url()
      const isLoginPage = this.options.loginPages.some(lp => url.includes(lp))

      if (isLoginPage && frame === this.page.mainFrame()) {
        this.recordEvent({
          type: 'unexpected_login_redirect',
          timestamp: Date.now(),
          url,
          message: `Page redirected to login: ${url}`,
        })
      }
    }

    this.page.on('framenavigated', handler)
    this.cleanupFns.push(() => this.page.off('framenavigated', handler))
  }

  /** Watch for Element Plus error toast messages */
  private watchToastMessages(): void {
    // Inject a MutationObserver to detect ElMessage toast appearances
    const observerScript = () => {
      const observer = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
          for (const node of mutation.addedNodes) {
            if (node instanceof Element) {
              const el = node.closest('.el-message--error') || node.querySelector('.el-message--error')
              if (el) {
                const text = el.textContent || ''
                const authKeywords = [
                  '登录已过期',
                  '请重新登录',
                  'Token 刷新失败',
                  '没有权限',
                  '用户未登录',
                  'Token已过期',
                ]
                const matched = authKeywords.find(kw => text.includes(kw))
                if (matched) {
                  // Dispatch a custom event that Playwright can listen for
                  window.dispatchEvent(new CustomEvent('__auth_monitor_issue', {
                    detail: { message: matched, fullText: text },
                  }))
                }
              }
            }
          }
        }
      })

      observer.observe(document.body, { childList: true, subtree: true })
      return observer
    }

    this.page.evaluate(observerScript).catch(() => {})

    // Listen for the custom event via page.evaluate exposed callback
    const toastHandler = async (event: { type: string; detail?: { message: string; fullText: string } }) => {
      if (!this.running || event.type !== '__auth_monitor_issue') return
      const msg = event.detail?.message || ''
      const fullText = event.detail?.fullText || ''

      if (msg.includes('没有权限')) {
        this.recordEvent({
          type: 'permission_denied',
          timestamp: Date.now(),
          message: fullText,
        })
      } else if (msg.includes('Token 刷新失败')) {
        this.recordEvent({
          type: 'token_refresh_failed',
          timestamp: Date.now(),
          message: fullText,
        })
      } else {
        this.recordEvent({
          type: 'token_expired',
          timestamp: Date.now(),
          message: fullText,
        })
      }
    }

    // Use page.exposeFunction to receive toast events from the page
    this.page.exposeFunction('__onAuthToast', (detail: { message: string; fullText: string }) => {
      toastHandler({ type: '__auth_monitor_issue', detail })
    }).then(() => {
      // Update the observer to call the exposed function
      return this.page.evaluate(() => {
        window.addEventListener('__auth_monitor_issue', ((e: CustomEvent) => {
          if ((window as any).__onAuthToast) {
            (window as any).__onAuthToast(e.detail)
          }
        }) as EventListener)
      })
    }).catch(() => {})
  }
}

/**
 * Helper to check if a page is currently showing an auth error state
 */
export async function isAuthErrorPresent(page: Page): Promise<boolean> {
  const url = page.url()
  if (url.includes('/login')) return true

  const errorToast = page.locator('.el-message--error')
  if (await errorToast.isVisible().catch(() => false)) {
    const text = await errorToast.textContent().catch(() => '')
    const authKeywords = ['登录已过期', '请重新登录', 'Token 刷新失败', '没有权限', '用户未登录']
    return authKeywords.some(kw => text?.includes(kw))
  }

  return false
}

/**
 * Check if the page has a valid token in sessionStorage
 */
export async function hasValidToken(page: Page): Promise<boolean> {
  const token = await page.evaluate(() => sessionStorage.getItem('access_token'))
  return !!token && token.length > 20
}
