/**
 * IntersectionObserver composable for lazy-loading content.
 * Triggers a callback when an element enters the viewport.
 * Useful for deferring expensive chart renders until visible.
 */

import { ref, onMounted, onBeforeUnmount, type Ref } from 'vue'

interface UseIntersectionObserverOptions {
  threshold?: number
  rootMargin?: string
  immediate?: boolean
}

export function useIntersectionObserver(
  targetRef: Ref<HTMLElement | null>,
  callback: () => void,
  options: UseIntersectionObserverOptions = {}
) {
  const isVisible = ref(false)
  const hasTriggered = ref(false)
  let observer: IntersectionObserver | null = null

  const { threshold = 0.1, rootMargin = '100px', immediate = false } = options

  if (immediate) {
    hasTriggered.value = true
    isVisible.value = true
  }

  function start() {
    if (hasTriggered.value || !targetRef.value) return
    if (typeof IntersectionObserver === 'undefined') {
      // Fallback: trigger immediately if IO not available (e.g., jsdom)
      isVisible.value = true
      hasTriggered.value = true
      callback()
      return
    }

    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting && !hasTriggered.value) {
            isVisible.value = true
            hasTriggered.value = true
            callback()
            observer?.disconnect()
            break
          }
        }
      },
      { threshold, rootMargin }
    )

    observer.observe(targetRef.value)
  }

  onMounted(start)

  onBeforeUnmount(() => {
    observer?.disconnect()
  })

  return { isVisible, hasTriggered }
}