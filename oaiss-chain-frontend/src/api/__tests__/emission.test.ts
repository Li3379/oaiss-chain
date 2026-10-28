import { describe, it, expect, vi } from 'vitest'
vi.mock('../request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
describe('Emission API', () => {
  it('should export functions', async () => {
    const mod = await import('../../api/emission')
    expect(Object.keys(mod).length).toBeGreaterThan(0)
  })
})