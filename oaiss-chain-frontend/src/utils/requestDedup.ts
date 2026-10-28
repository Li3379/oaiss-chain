/**
 * Request Deduplication Utility
 *
 * Prevents duplicate concurrent API calls by caching in-flight requests.
 * When the same GET request is made multiple times concurrently, only one
 * actual HTTP request is made and the result is shared.
 *
 * @author OAISS Chain Team
 * @since 1.3.0
 */

const inflightRequests = new Map<string, Promise<unknown>>()

/**
 * Generate a deduplication key from request config.
 */
function getDedupKey(method: string, url: string, params?: Record<string, unknown>): string {
  const sortedParams = params
    ? JSON.stringify(Object.entries(params).sort(([a], [b]) => a.localeCompare(b)))
    : ''
  return `${method.toUpperCase()}:${url}:${sortedParams}`
}

/**
 * Deduplicate concurrent GET requests.
 * Returns the same promise for identical in-flight requests.
 */
export function dedupRequest<T>(
  key: string,
  requestFn: () => Promise<T>,
): Promise<T> {
  const existing = inflightRequests.get(key) as Promise<T> | undefined
  if (existing) {
    return existing
  }

  const promise = requestFn().finally(() => {
    inflightRequests.delete(key)
  })

  inflightRequests.set(key, promise)
  return promise
}

export { getDedupKey }
