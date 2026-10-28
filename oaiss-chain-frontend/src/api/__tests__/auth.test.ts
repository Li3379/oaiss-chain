import { describe, it, expect, vi } from 'vitest'

vi.mock('../request', () => ({
  default: {
    post: vi.fn().mockResolvedValue({}),
    get: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
  },
}))

describe('Auth API', () => {
  it('login endpoint should be defined', async () => {
    const mod = await import('../../api/auth')
    expect(mod.login).toBeDefined()
    expect(typeof mod.login).toBe('function')
  })

  it('register endpoint should be defined', async () => {
    const mod = await import('../../api/auth')
    expect(mod.register).toBeDefined()
  })

  it('logout endpoint should be defined', async () => {
    const mod = await import('../../api/auth')
    expect(mod.logout).toBeDefined()
  })

  it('checkIp endpoint should be defined', async () => {
    const mod = await import('../../api/auth')
    expect(mod.checkIp).toBeDefined()
  })

  it('getCurrentUser endpoint should be defined', async () => {
    const mod = await import('../../api/auth')
    expect(mod.getCurrentUser).toBeDefined()
  })
})
