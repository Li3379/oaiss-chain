import { describe, it, expect, vi } from 'vitest'
vi.mock('../request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
describe('Reviewer API', () => {
  it('should export functions', async () => {
    const mod = await import('../../api/reviewer')
    expect(Object.keys(mod).length).toBeGreaterThan(0)
  })
})