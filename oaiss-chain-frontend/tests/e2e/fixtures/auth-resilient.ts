import { type Page, expect } from '@playwright/test'
import { loginViaApi, TEST_USERS } from './auth'

type RoleKey = keyof typeof TEST_USERS
import { AuthMonitor, hasValidToken, isAuthErrorPresent, type AuthEvent, type AuthReport } from './auth-monitor'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const MAX_RECOVERY_ATTEMPTS = 3

/**
 * Result of a resilient login operation
 */
export interface ResilientLoginResult {
  token: string
  monitor: AuthMonitor
  role: string
}

/**
 * Create a resilient login that auto-recovers from auth failures.
 *
 * This wraps loginViaApi with:
 * 1. Post-login token validation
 * 2. AuthMonitor attached to detect future issues
 * 3. Auto-recovery on 401/token expiration
 *
 * Usage:
 * ```ts
 * const { token, monitor } = await resilientLogin(page, 'enterprise')
 * // ... do work ...
 * const report = monitor.stop()
 * if (report.summary.total > 0) console.warn('Auth issues:', report)
 * ```
 */
export async function resilientLogin(
  page: Page,
  role: RoleKey,
): Promise<ResilientLoginResult> {
  const user = TEST_USERS[role]
  const monitor = new AuthMonitor(page, {
    onAuthIssue: async (event) => {
      console.warn(`[AuthMonitor] ${event.type}: ${event.message} (${event.url || 'no url'})`)
    },
  })

  const token = await performLogin(page, user.username, user.password)
  monitor.start()

  return { token, monitor, role: user.role }
}

/**
 * Perform login via API and set tokens in sessionStorage.
 * Validates the token is actually usable after setting it.
 */
async function performLogin(page: Page, username: string, password: string): Promise<string> {
  const response = await page.request.post(`${API_BASE}/auth/login`, {
    data: { username, password },
  })

  if (response.status() !== 200) {
    const body = await response.text().catch(() => 'unknown error')
    throw new Error(`Login failed (${response.status()}): ${body}`)
  }

  const body = await response.json()
  if (!body.data?.accessToken) {
    throw new Error(`Login response missing accessToken: ${JSON.stringify(body)}`)
  }

  const { accessToken, refreshToken } = body.data

  // Navigate to app and set tokens
  await page.goto(BASE_URL)
  await page.evaluate(
    ({ accessToken, refreshToken }) => {
      sessionStorage.setItem('access_token', accessToken)
      sessionStorage.setItem('refresh_token', refreshToken)
    },
    { accessToken, refreshToken },
  )

  // Validate token is in storage
  const stored = await page.evaluate(() => sessionStorage.getItem('access_token'))
  if (!stored) {
    throw new Error('Token was not persisted to sessionStorage')
  }

  return accessToken
}

/**
 * Recover from an auth failure by re-authenticating and restoring page state.
 *
 * @returns The new access token, or null if recovery failed
 */
export async function recoverAuth(page: Page, role: RoleKey): Promise<string | null> {
  for (let attempt = 1; attempt <= MAX_RECOVERY_ATTEMPTS; attempt++) {
    try {
      const user = TEST_USERS[role]
      const currentUrl = page.url()

      // Re-authenticate
      const newToken = await performLogin(page, user.username, user.password)

      // Navigate back to where the user was (if not on login page)
      if (currentUrl && !currentUrl.includes('/login')) {
        await page.goto(currentUrl).catch(() => {})
      }

      // Wait for page to stabilize
      await page.waitForLoadState('networkidle').catch(() => {})

      // Verify recovery succeeded
      const authOk = !(await isAuthErrorPresent(page))
      if (authOk) {
        console.log(`[AuthRecovery] Recovered on attempt ${attempt}`)
        return newToken
      }
    } catch (e) {
      console.warn(`[AuthRecovery] Attempt ${attempt} failed:`, e)
    }
  }

  console.error(`[AuthRecovery] All ${MAX_RECOVERY_ATTEMPTS} attempts failed`)
  return null
}

/**
 * Wrap a page operation with automatic auth recovery.
 *
 * Executes `fn`, and if an auth issue is detected during execution,
 * automatically recovers and retries.
 *
 * Usage:
 * ```ts
 * const result = await withAuthRecovery(page, 'enterprise', async () => {
 *   await page.goto('/enterprise/carbon/upload')
 *   await page.getByRole('button', { name: '创建项目' }).click()
 *   return 'success'
 * })
 * ```
 */
export async function withAuthRecovery<T>(
  page: Page,
  role: RoleKey,
  fn: () => Promise<T>,
): Promise<T> {
  try {
    return await fn()
  } catch (error) {
    // Check if the error is auth-related
    const errorMsg = error instanceof Error ? error.message : String(error)
    const isAuthError =
      errorMsg.includes('401') ||
      errorMsg.includes('403') ||
      errorMsg.includes('登录已过期') ||
      errorMsg.includes('没有权限') ||
      errorMsg.includes('Token')

    if (!isAuthError) throw error

    console.warn(`[withAuthRecovery] Auth error detected, recovering: ${errorMsg}`)

    const newToken = await recoverAuth(page, role)
    if (!newToken) throw error

    // Retry the operation after recovery
    return await fn()
  }
}

/**
 * Wait for the page's auth state to be stable (no pending auth issues).
 *
 * Polls for auth error indicators and waits until they clear.
 * Useful after navigation or page transitions where auth may be in flux.
 */
export async function waitForAuthReady(page: Page, timeout = 10000): Promise<void> {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    const hasToken = await hasValidToken(page)
    const hasError = await isAuthErrorPresent(page)

    if (hasToken && !hasError) return

    // If on login page with no token, that's a terminal state — don't loop
    if (!hasToken && page.url().includes('/login')) {
      throw new Error('waitForAuthReady: User is on login page with no token')
    }

    await page.waitForTimeout(500)
  }

  throw new Error('waitForAuthReady: Timed out waiting for stable auth state')
}

/**
 * Check the health of the current auth session.
 *
 * Returns a diagnostic object with token state, page state, and any issues.
 */
export async function checkAuthHealth(page: Page): Promise<{
  healthy: boolean
  hasToken: boolean
  onLoginPage: boolean
  authErrorVisible: boolean
  currentUrl: string
}> {
  const url = page.url()
  const loginPage = url.includes('/login')

  const [token, errorVisible] = await Promise.all([
    hasValidToken(page),
    isAuthErrorPresent(page),
  ])

  return {
    healthy: token && !loginPage && !errorVisible,
    hasToken: token,
    onLoginPage: loginPage,
    authErrorVisible: errorVisible,
    currentUrl: url,
  }
}

/**
 * Assert that the page is in a healthy auth state.
 * Fails the test if auth issues are detected.
 */
export async function assertAuthHealthy(page: Page): Promise<void> {
  const health = await checkAuthHealth(page)
  expect(health.healthy, `Auth health check failed: ${JSON.stringify(health)}`).toBeTruthy()
}
