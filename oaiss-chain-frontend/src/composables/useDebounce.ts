/**
 * Debounce composable for delaying expensive operations.
 * Returns a debounced version of the callback that only executes
 * after `delayMs` milliseconds of inactivity.
 */

import { ref, onBeforeUnmount } from 'vue'

export function useDebounce<T extends (...args: unknown[]) => void>(
  callback: T,
  delayMs: number = 300
) {
  const timer = ref<ReturnType<typeof setTimeout> | null>(null)
  const isPending = ref(false)

  function debounced(...args: Parameters<T>) {
    if (timer.value) clearTimeout(timer.value)
    isPending.value = true
    timer.value = setTimeout(() => {
      callback(...args)
      isPending.value = false
    }, delayMs)
  }

  function cancel() {
    if (timer.value) {
      clearTimeout(timer.value)
      timer.value = null
    }
    isPending.value = false
  }


  onBeforeUnmount(cancel)

  return { debounced, cancel, isPending }
}