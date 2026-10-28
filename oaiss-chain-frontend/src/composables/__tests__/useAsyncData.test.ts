import { describe, it, expect, vi } from 'vitest'
import { useAsyncData } from '../useAsyncData'

describe('useAsyncData', () => {
  it('resolves data on success', async () => {
    const fetcher = vi.fn().mockResolvedValue({ id: 1, name: 'test' })
    const { data, loading, error, execute } = useAsyncData(fetcher)

    expect(data.value).toBeNull()
    expect(loading.value).toBe(false)

    const promise = execute()
    expect(loading.value).toBe(true)

    await promise
    expect(data.value).toEqual({ id: 1, name: 'test' })
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('captures error message on failure', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('Network error'))
    const { data, error, execute } = useAsyncData(fetcher)

    await execute()
    expect(data.value).toBeNull()
    expect(error.value).toBe('Network error')
  })

  it('handles non-Error thrown values', async () => {
    const fetcher = vi.fn().mockRejectedValue('string error')
    const { error, execute } = useAsyncData(fetcher)

    await execute()
    expect(error.value).toBe('string error')
  })

  it('reset clears all state', async () => {
    const fetcher = vi.fn().mockResolvedValue(42)
    const { data, loading, error, execute, reset } = useAsyncData(fetcher)

    await execute()
    expect(data.value).toBe(42)

    reset()
    expect(data.value).toBeNull()
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('calls fetcher immediately when immediate option is set', async () => {
    const fetcher = vi.fn().mockResolvedValue('auto')
    const { data } = useAsyncData(fetcher, { immediate: true })

    // Wait for microtask to complete
    await vi.waitFor(() => {
      expect(data.value).toBe('auto')
    })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})
