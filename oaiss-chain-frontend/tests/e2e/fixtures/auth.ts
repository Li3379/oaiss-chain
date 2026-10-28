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

export async function loginViaToken(page: Page, role: string): Promise<void> {
  const token = getToken(role)
  await page.addInitScript(
    (args) => {
      localStorage.setItem('access_token', args.token)
      localStorage.setItem('refresh_token', `${args.roleLabel.toLowerCase()}-refresh-token`)
      localStorage.setItem('remember_me', 'true')
      localStorage.setItem('user_role', args.roleLabel)
      sessionStorage.setItem('access_token', args.token)
      sessionStorage.setItem('refresh_token', `${args.roleLabel.toLowerCase()}-refresh-token`)
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
