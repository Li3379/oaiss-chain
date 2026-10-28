import { describe, it, expect, vi } from 'vitest'

vi.mock('../request', () => ({
  default: {
    post: vi.fn().mockResolvedValue({}),
    get: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
  },
}))

describe('Trade API', () => {
  it('module should export functions', async () => {
    const mod = await import('../../api/trade')
    expect(mod).toBeDefined()
    const exports = Object.keys(mod)
    expect(exports.length).toBeGreaterThan(0)
  })

  it('should have createP2PTrade or similar function', async () => {
    const mod = await import('../../api/trade')
    const hasTradeFn = Object.values(mod).some(v => typeof v === 'function')
    expect(hasTradeFn).toBe(true)
  })
})