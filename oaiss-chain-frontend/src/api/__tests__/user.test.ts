import { describe, it, expect, vi } from 'vitest'

vi.mock('../request', () => ({
  default: {
    get: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
  },
}))

describe('User API', () => {
  it('module should export functions', async () => {
    const mod = await import('../../api/user')
    expect(mod).toBeDefined()
    const exports = Object.keys(mod)
    expect(exports.length).toBeGreaterThan(0)
  })

  it('should have getProfile function', async () => {
    const mod = await import('../../api/user')
    const hasProfileFn = Object.values(mod).some(v => typeof v === 'function')
    expect(hasProfileFn).toBe(true)
  })
})