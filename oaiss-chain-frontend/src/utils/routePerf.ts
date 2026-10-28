/**
 * Route Performance Observer
 *
 * Tracks navigation timing per route and reports slow navigations.
 * Complements Web Vitals by measuring route-specific performance.
 *
 * @author OAISS Chain Team
 * @since 1.3.0
 */

import type { Router } from 'vue-router'

interface RouteMetric {
  path: string
  durationMs: number
  timestamp: number
}

const SLOW_NAVIGATION_THRESHOLD_MS = 2000
const routeMetrics: RouteMetric[] = []
let navigationStart = 0

/**
 * Install route performance observer on the Vue Router.
 */
export function installRoutePerfObserver(router: Router): void {
  router.beforeEach(() => {
    navigationStart = performance.now()
  })

  router.afterEach((to) => {
    if (navigationStart === 0) return

    const durationMs = performance.now() - navigationStart
    const metric: RouteMetric = {
      path: to.path,
      durationMs: Math.round(durationMs * 100) / 100,
      timestamp: Date.now(),
    }

    routeMetrics.push(metric)

    // Keep only last 50 metrics in memory
    if (routeMetrics.length > 50) {
      routeMetrics.shift()
    }

    if (durationMs > SLOW_NAVIGATION_THRESHOLD_MS) {
      console.warn(
        `[RoutePerf] Slow navigation to ${to.path}: ${durationMs.toFixed(0)}ms`
      )
    }

    navigationStart = 0
  })
}

/**
 * Get collected route metrics (for debugging or reporting).
 */
export function getRouteMetrics(): RouteMetric[] {
  return [...routeMetrics]
}
