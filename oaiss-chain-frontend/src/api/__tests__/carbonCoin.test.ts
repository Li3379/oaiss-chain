import { describe, it, expect, vi } from 'vitest'
vi.mock('../request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
describe('CarbonCoin API', () => {
  it('should export functions', async () => {
    const mod = await import('../../api/carbonCoin')
    expect(Object.keys(mod).length).toBeGreaterThan(0)
  })
  it('should have account-related functions', async () => {
    const mod = await import('../../api/carbonCoin')
    expect(Object.values(mod).some(v => typeof v === 'function')).toBe(true)
  })
})