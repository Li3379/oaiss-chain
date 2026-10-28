import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ROLE } from '../../config/menu'
import { getAccessToken, setTokens, clearTokens } from '../../utils/auth'
import { useAppStore } from '../index'

function createJwt(payload: Record<string, unknown>, header: Record<string, unknown> = { alg: 'HS256' }) {
  const encode = (value: Record<string, unknown>) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode(header)}.${encode(payload)}.signature`
}

describe('app store auth state', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
    clearTokens()
  })

  it('hydrates reviewer home path from roles claim', () => {
    const token = createJwt({ sub: 'reviewer01', roles: [ROLE.REVIEWER], userId: 2, exp: 9999999999 })
    setTokens(token, 'refresh-token')

    const store = useAppStore()

    expect(store.loggedIn).toBe(true)
    expect(store.role).toBe(ROLE.REVIEWER)
    expect(store.homePath).toBe('/auditor/audit/list')
  })

  it('falls back to userType when roles claim is missing', () => {
    const token = createJwt({ sub: 'reviewer02', userType: 2, userId: 3, exp: 9999999999 })
    setTokens(token, 'refresh-token')

    const store = useAppStore()

    expect(store.loggedIn).toBe(true)
    expect(store.role).toBe(ROLE.REVIEWER)
    expect(store.homePath).toBe('/auditor/audit/list')
  })

  it('treats tokens without a resolvable role as logged out', () => {
    const token = createJwt({ sub: 'unknown-role', userId: 9, exp: 9999999999 })
    setTokens(token, 'refresh-token')

    const store = useAppStore()

    expect(store.loggedIn).toBe(false)
    expect(store.role).toBeNull()
    expect(store.homePath).toBe('/official-home')
    expect(getAccessToken()).toBeNull()
  })

  it('rejects invalid login tokens and clears auth state', () => {
    const store = useAppStore()
    const token = createJwt({ sub: 'unknown-role', userId: 9, exp: 9999999999 })

    const loggedIn = store.login({ accessToken: token, refreshToken: 'refresh-token' })

    expect(loggedIn).toBe(false)
    expect(store.loggedIn).toBe(false)
    expect(store.role).toBeNull()
    expect(getAccessToken()).toBeNull()
  })
})
