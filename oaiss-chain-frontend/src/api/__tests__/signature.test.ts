import { describe, it, expect, vi } from 'vitest'
vi.mock('../request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
describe('Signature API', () => {
  it('should export functions', async () => {
    const mod = await import('../../api/signature')
    expect(Object.keys(mod).length).toBeGreaterThan(0)
  })
})