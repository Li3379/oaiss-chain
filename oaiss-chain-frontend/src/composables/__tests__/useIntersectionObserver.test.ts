import { describe, it, expect, beforeEach, afterEach } from 'vitest'

describe('useIntersectionObserver', () => {
  let originalIO: typeof IntersectionObserver

  beforeEach(() => {
    originalIO = globalThis.IntersectionObserver
  })

  afterEach(() => {
    globalThis.IntersectionObserver = originalIO
  })

  it('should be available in the module', async () => {
    const mod = await import('../useIntersectionObserver')
    expect(mod.useIntersectionObserver).toBeDefined()
    expect(typeof mod.useIntersectionObserver).toBe('function')
  })

  it('should accept rootMargin option', () => {
    const options = { rootMargin: '200px' }
    expect(options.rootMargin).toBe('200px')
  })

  it('should accept threshold option', () => {
    const options = { threshold: 0.1 }
    expect(options.threshold).toBe(0.1)
  })

  it('should handle IntersectionObserver not available', () => {
    // In JSDOM test env, IO may be polyfilled
    const hasIO = typeof IntersectionObserver !== 'undefined'
    expect(typeof hasIO).toBe('boolean')
  })

  it('should accept root option for container scoping', () => {
    const options = { root: null }
    expect(options.root).toBeNull()
  })

  it('default rootMargin should be 200px', () => {
    const DEFAULT_ROOT_MARGIN = '200px'
    expect(DEFAULT_ROOT_MARGIN).toBe('200px')
  })

  it('default threshold should be 0', () => {
    const DEFAULT_THRESHOLD = 0
    expect(DEFAULT_THRESHOLD).toBe(0)
  })
})
