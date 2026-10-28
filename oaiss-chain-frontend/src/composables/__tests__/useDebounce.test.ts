import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useDebounce } from '../useDebounce'

vi.mock('vue', async () => {
  const actual = await vi.importActual('vue')
  return { ...actual, onBeforeUnmount: vi.fn() }
})

describe('useDebounce', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('delays callback execution', () => {
    const cb = vi.fn()
    const { debounced } = useDebounce(cb, 300)
    debounced()
    expect(cb).not.toHaveBeenCalled()
    vi.advanceTimersByTime(300)
    expect(cb).toHaveBeenCalledOnce()
  })

  it('resets timer on repeated calls', () => {
    const cb = vi.fn()
    const { debounced } = useDebounce(cb, 300)
    debounced()
    vi.advanceTimersByTime(200)
    debounced()
    vi.advanceTimersByTime(200)
    expect(cb).not.toHaveBeenCalled()
    vi.advanceTimersByTime(100)
    expect(cb).toHaveBeenCalledOnce()
  })

  it('cancel prevents execution', () => {
    const cb = vi.fn()
    const { debounced, cancel } = useDebounce(cb, 300)
    debounced()
    cancel()
    vi.advanceTimersByTime(500)
    expect(cb).not.toHaveBeenCalled()
  })

  it('isPending reflects state', () => {
    const cb = vi.fn()
    const { debounced, isPending } = useDebounce(cb, 300)
    expect(isPending.value).toBe(false)
    debounced()
    expect(isPending.value).toBe(true)
    vi.advanceTimersByTime(300)
    expect(isPending.value).toBe(false)
  })
})