/**
 * Async data fetching composable with loading, error, and retry support.
 * Inspired by Vue.js official composable patterns for reusable data fetching.
 */
import { ref, type Ref } from 'vue'

export interface UseAsyncDataOptions {
  /** Auto-fetch on mount (default: false) */
  immediate?: boolean
}

export interface UseAsyncDataReturn<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<string | null>
  execute: () => Promise<void>
  reset: () => void
}

export function useAsyncData<T>(
  fetcher: () => Promise<T>,
  options: UseAsyncDataOptions = {}
): UseAsyncDataReturn<T> {
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function execute() {
    loading.value = true
    error.value = null
    try {
      data.value = await fetcher()
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
      data.value = null
    } finally {
      loading.value = false
    }
  }

  function reset() {
    data.value = null
    loading.value = false
    error.value = null
  }

  if (options.immediate) {
    execute()
  }

  return { data, loading, error, execute, reset }
}
