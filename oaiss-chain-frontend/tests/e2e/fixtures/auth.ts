import { type Page } from '@playwright/test'
import { AuthMonitor } from './auth-monitor'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const RATE_LIMIT_CODE = 1010
const TOKEN_EXPIRY_SKEW_MS = 30_000
const LOGIN_RETRY_DELAY_MS = 2_500

type TokenBundle = {
  accessToken: string
  refreshToken: string
  expiresAt: number
}

const tokenCache = new Map<string, TokenBundle>()

const USERNAME_FALLBACKS: Record<string, string[]> = {
  admin: ['admin'],
  enterprise001: ['enterprise001', 'enterprise002', 'enterprise003'],
  enterprise002: ['enterprise002', 'enterprise003', 'enterprise001'],
  enterprise003: ['enterprise003', 'enterprise001', 'enterprise002'],
  reviewer001: ['reviewer001'],
  thirdparty001: ['thirdparty001'],
}

export const MOCK_TOKENS: Record<string, string> = {
  ENTERPRISE:
    'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJlbnRlcnByaXNlMDAxIiwidXNlcklkIjoyLCJlbnRlcnByaXNlSWQiOjEsInJvbGVzIjpbIkVOVEVSUFJJU0UiXSwidXNlclR5cGUiOjEsImV4cCI6OTk5OTk5OTk5OX0.mock',
  ADMIN:
    'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInVzZXJJZCI6MSwiZW50ZXJwcmlzZUlkIjoxLCJyb2xlcyI6WyJBRE1JTiJdLCJ1c2VyVHlwZSI6NCwiZXhwIjo5OTk5OTk5OTk5fQ.mock',
  REVIEWER:
    'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyZXZpZXdlciIsInVzZXJJZCI6NCwiZW50ZXJwcmlzZUlkIjoxLCJyb2xlcyI6WyJSRVZJRVdFUiJdLCJ1c2VyVHlwZSI6MiwiZXhwIjo5OTk5OTk5OTk5fQ.mock',
  THIRD_PARTY:
    'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0aGlyZHBhcnR5IiwidXNlcklkIjo1LCJlbnRlcnByaXNlSWQiOjEsInJvbGVzIjpbIlRISVJEX1BBUlRZIl0sInVzZXJUeXBlIjozLCJleHAiOjk5OTk5OTk5OTl9.mock',
}

const TEST_PASSWORD = process.env.TEST_USER_PASSWORD || 'admin123'

export const TEST_USERS: Record<string, { username: string; password: string; role: string }> = {
  admin: { username: 'admin', password: TEST_PASSWORD, role: 'ADMIN' },
  enterprise: { username: 'enterprise001', password: TEST_PASSWORD, role: 'ENTERPRISE' },
  reviewer: { username: 'reviewer001', password: TEST_PASSWORD, role: 'REVIEWER' },
  thirdParty: { username: 'thirdparty001', password: TEST_PASSWORD, role: 'THIRD_PARTY' },
}

/** 角色名 → TEST_USERS 键，供 loginViaToken 优先真实登录用 */
const ROLE_TO_TEST_USER: Record<string, string> = {
  ENTERPRISE: 'enterprise',
  REVIEWER: 'reviewer',
  THIRD_PARTY: 'thirdParty',
  ADMIN: 'admin',
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function getExpiryTimestamp(accessToken: string): number {
  try {
    const payload = JSON.parse(atob(accessToken.split('.')[1] || ''))
    if (payload?.exp) {
      return Number(payload.exp) * 1000
    }
  } catch {
    // Ignore malformed JWT payloads in tests and fall back to a short cache window.
  }

  return Date.now() + 5 * 60 * 1000
}

async function validateCachedToken(page: Page, token: string): Promise<boolean> {
  const response = await page.request.get(`${API_BASE}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok()) {
    return false
  }

  const body = await response.json().catch(() => null)
  return body?.code === 200
}

async function persistAuth(page: Page, accessToken: string, refreshToken: string): Promise<void> {
  await page.goto(BASE_URL)
  await page.evaluate(
    ({ accessToken: nextAccessToken, refreshToken: nextRefreshToken }) => {
      sessionStorage.clear()
      localStorage.clear()

      localStorage.setItem('locale', 'zh-CN')
      localStorage.setItem('access_token', nextAccessToken)
      localStorage.setItem('refresh_token', nextRefreshToken)
      localStorage.setItem('remember_me', 'true')
      sessionStorage.setItem('access_token', nextAccessToken)
      sessionStorage.setItem('refresh_token', nextRefreshToken)
      sessionStorage.setItem('remember_me', 'true')

      try {
        const parts = nextAccessToken.split('.')
        if (parts.length === 3) {
          const payload = JSON.parse(atob(parts[1]))
          if (payload.exp) {
            localStorage.setItem('token_expiry', String(payload.exp * 1000))
            sessionStorage.setItem('token_expiry', String(payload.exp * 1000))
          }
          // 预置「新用户 3 步上手引导」已完成标记（键按 userId 隔离）。
          // 不预置的话，每次全新上下文都会弹出全屏引导遮罩，
          // 把页面上所有按钮盖住——表现为「元素存在但 click 超时」，
          // 让十几个 probe 挂在与其主题完全无关的失败上。
          if (payload.userId) {
            localStorage.setItem(`oaiss_guided_tour_done:${payload.userId}`, '1')
          }
        }
      } catch {
        // JWT parse failed, skip expiry extraction
      }
    },
    { accessToken, refreshToken },
  )
}

function getLoginCandidates(username: string): string[] {
  return USERNAME_FALLBACKS[username] || [username]
}

async function requestLogin(page: Page, username: string, password: string) {
  const response = await page.request.post(`${API_BASE}/auth/login`, {
    headers: { 'Content-Type': 'application/json' },
    data: { username, password },
  })
  const body = await response.json()

  return { response, body }
}

export function getToken(role: string): string {
  return MOCK_TOKENS[role] || MOCK_TOKENS.ENTERPRISE
}

/**
 * 注入登录态。
 *
 * 历史实现直接注入 MOCK_TOKENS 里的假签名 JWT。后端加入 JWT 签名校验后，
 * 这些假 token 一到 `/auth/me` 等真实端点就 401，响应拦截器随即 clearTokens()
 * 并跳登录页——于是所有依赖本函数的 smoke 用例都挂在「菜单不可见」这类表象上。
 *
 * 现在的策略：**优先真实登录**（用户名/密码换真 token），只有真实登录不可用时
 * 才回退到 mock token。这样 smoke 不再依赖「后端不校验签名」这个已不成立的假设。
 */
export async function loginViaToken(page: Page, role: string): Promise<void> {
  const testUser = TEST_USERS[ROLE_TO_TEST_USER[role] ?? 'enterprise']
  if (testUser) {
    try {
      await loginViaApi(page, testUser.username, testUser.password)
      return
    } catch {
      // 后端不可用或登录被拒：退回 mock token，让纯前端断言仍可运行
    }
  }

  const token = getToken(role)
  await page.addInitScript(
    (args) => {
      localStorage.setItem('access_token', args.token)
      localStorage.setItem('refresh_token', `${args.roleLabel.toLowerCase()}-refresh-token`)
      localStorage.setItem('remember_me', 'true')
      localStorage.setItem('user_role', args.roleLabel)
      sessionStorage.setItem('access_token', args.token)
      sessionStorage.setItem('refresh_token', `${args.roleLabel.toLowerCase()}-refresh-token`)
      // 同时抑制新用户引导遮罩（mock token 里也带 userId 时按 userId 存，否则存全局键）
      try {
        const payload = JSON.parse(atob(args.token.split('.')[1] || ''))
        const key = payload?.userId
          ? `oaiss_guided_tour_done:${payload.userId}`
          : 'oaiss_guided_tour_done'
        localStorage.setItem(key, '1')
        sessionStorage.setItem(key, '1')
      } catch {
        localStorage.setItem('oaiss_guided_tour_done', '1')
        sessionStorage.setItem('oaiss_guided_tour_done', '1')
      }
    },
    { token, roleLabel: role },
  )
}

export async function loginViaApi(
  page: Page,
  username: string,
  password: string,
): Promise<string> {
  const cached = tokenCache.get(username)
  if (cached && cached.expiresAt - TOKEN_EXPIRY_SKEW_MS > Date.now()) {
    const stillValid = await validateCachedToken(page, cached.accessToken)
    if (stillValid) {
      await persistAuth(page, cached.accessToken, cached.refreshToken)
      return cached.accessToken
    }

    tokenCache.delete(username)
  }

  let lastError = ''

  for (const candidateUsername of getLoginCandidates(username)) {
    const { response, body } = await requestLogin(page, candidateUsername, password)

    if (response.ok() && body.code === 200 && body.data?.accessToken) {
      const accessToken = body.data.accessToken as string
      const refreshToken = body.data.refreshToken as string
      const tokenBundle = {
        accessToken,
        refreshToken,
        expiresAt: getExpiryTimestamp(accessToken),
      }
      tokenCache.set(candidateUsername, tokenBundle)
      if (candidateUsername !== username) {
        tokenCache.set(username, tokenBundle)
      }
      await persistAuth(page, accessToken, refreshToken)
      return accessToken
    }

    lastError = `status=${response.status()}, body=${JSON.stringify(body)}`
    if (body?.code === RATE_LIMIT_CODE) {
      await sleep(LOGIN_RETRY_DELAY_MS)
      continue
    }

    throw new Error(`Login failed: ${lastError}`)
  }

  throw new Error(`Login failed: ${lastError}`)
}

export function buildStorageState(role = 'ENTERPRISE') {
  const token = getToken(role)
  const origin = new URL(BASE_URL).origin
  return {
    origins: [
      {
        origin,
        localStorage: [
          { name: 'access_token', value: token },
          { name: 'user_role', value: role },
        ],
      },
    ],
  }
}

/**
 * Login via API and return both token and an attached AuthMonitor.
 *
 * Usage:
 * ```ts
 * const { token, monitor } = await loginWithMonitor(page, 'enterprise')
 * // ... run test operations ...
 * const report = monitor.stop()
 * ```
 */
export async function loginWithMonitor(
  page: Page,
  role: keyof typeof TEST_USERS,
): Promise<{ token: string; monitor: AuthMonitor }> {
  const user = TEST_USERS[role]
  const monitor = new AuthMonitor(page)
  monitor.start()

  const token = await loginViaApi(page, user.username, user.password)
  return { token, monitor }
}
