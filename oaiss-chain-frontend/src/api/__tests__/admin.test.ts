import { describe, it, expect, vi } from 'vitest'
vi.mock('../request', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
describe('Admin API', () => {
  it('should export functions', async () => {
    const mod = await import('../../api/admin')
    expect(Object.keys(mod).length).toBeGreaterThan(0)
  })
})