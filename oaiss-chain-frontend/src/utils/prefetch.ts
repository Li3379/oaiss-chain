/**
 * Prefetch a route component on hover/focus for faster navigation.
 * Usage: call prefetchRoute(() => import('../views/SomePage.vue')) on mouseenter.
 */
export function prefetchRoute(loader: () => Promise<unknown>) {
  loader()
}

/**
 * Prefetch critical routes after initial page load (idle callback).
 * Covers enterprise, admin, and auditor role dashboards.
 */
export function prefetchCriticalRoutes() {
  const criticalRoutes = [
    // Enterprise routes
    () => import('../views/enterprise/CompanyDashboard.vue'),
    () => import('../views/enterprise/CarbonUpload.vue'),
    () => import('../views/enterprise/TradingMarket.vue'),
    // Admin routes
    () => import('../views/admin/DataStatistics.vue'),
    () => import('../views/admin/SystemUsers.vue'),
    // Auditor routes
    () => import('../views/auditor/AuditList.vue'),
  ]

  if ('requestIdleCallback' in window) {
    window.requestIdleCallback(() => {
      criticalRoutes.forEach((route) => route())
    })
  } else {
    setTimeout(() => criticalRoutes.forEach((route) => route()), 2000)
  }
}