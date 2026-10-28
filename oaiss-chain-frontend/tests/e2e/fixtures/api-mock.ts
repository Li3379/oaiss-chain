import { type Page, type Route } from '@playwright/test'

interface MockRouteConfig {
  method: string
  path: string
  data: unknown
}

export function mockApiResponse(
  page: Page,
  method: string,
  path: string,
  data: unknown,
): void {
  const urlPattern = `**/api/v1${path}`
  page.route(urlPattern, (route: Route) => {
    if (route.request().method() === method) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data }),
      })
    } else {
      route.continue()
    }
  })
}

export function mockPaginatedList(
  page: Page,
  path: string,
  items: unknown[],
  total?: number,
): void {
  const totalElements = total ?? items.length
  const springPage = {
    content: items,
    totalElements,
    totalPages: Math.ceil(totalElements / 10),
    size: 10,
    number: 0,
    first: true,
    last: true,
    empty: items.length === 0,
  }
  mockApiResponse(page, 'GET', path, springPage)
}

export function setupSmokeMock(page: Page, role: string): void {
  const routes = SMOKE_ROUTES[role] || []
  for (const { method, path, data } of routes) {
    mockApiResponse(page, method, path, data)
  }
}

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true, empty: true }

const SMOKE_ROUTES: Record<string, MockRouteConfig[]> = {
  ENTERPRISE: [
    { method: 'GET', path: '/user/profile*', data: { id: 2, username: 'enterprise001', realName: 'Enterprise 001', userType: 1, email: 'enterprise001@example.com' } },
    { method: 'GET', path: '/carbon/my-reports*', data: EMPTY_PAGE },
    { method: 'GET', path: '/trade/my-trades*', data: EMPTY_PAGE },
    { method: 'GET', path: '/auction/orders*', data: EMPTY_PAGE },
    { method: 'GET', path: '/auction/my-orders*', data: EMPTY_PAGE },
    { method: 'GET', path: '/auction/results*', data: EMPTY_PAGE },
    { method: 'GET', path: '/carbon-neutral/search*', data: EMPTY_PAGE },
    { method: 'GET', path: '/carbon-neutral/my*', data: EMPTY_PAGE },
    { method: 'GET', path: '/credit/my-score*', data: { score: 100, level: 'EXCELLENT', tradeRestricted: false, accountFrozen: false } },
    { method: 'GET', path: '/credit/history*', data: EMPTY_PAGE },
    { method: 'GET', path: '/credit/ranking*', data: EMPTY_PAGE },
    { method: 'GET', path: '/carbon-coin/account*', data: { balance: 10000, totalRecharged: 10000, totalSpent: 0 } },
    { method: 'GET', path: '/carbon-coin/transactions*', data: EMPTY_PAGE },
    { method: 'GET', path: '/blockchain/status*', data: { connected: true, latestBlock: 100, peerCount: 5 } },
    { method: 'GET', path: '/blockchain/blocks/latest*', data: EMPTY_PAGE },
    { method: 'GET', path: '/blockchain/transactions*', data: EMPTY_PAGE },
    { method: 'GET', path: '/emission/my-rating*', data: [] },
    { method: 'GET', path: '/emission/ratings/*', data: [] },
    { method: 'GET', path: '/emission/rankings/*', data: [] },
    { method: 'GET', path: '/enterprise/info*', data: { id: 1, enterpriseName: 'Enterprise 001' } },
    { method: 'GET', path: '/enterprise/admission/my*', data: [] },
    { method: 'GET', path: '/signature/keypair*', data: null },
  ],
  ADMIN: [
    { method: 'GET', path: '/admin/users*', data: EMPTY_PAGE },
    { method: 'GET', path: '/carbon/reports*', data: EMPTY_PAGE },
    { method: 'GET', path: '/blockchain/status*', data: { status: 'healthy', connected: true, mode: 'MOCK' } },
    { method: 'GET', path: '/admin/statistics*', data: { totalUsers: 10, totalReports: 50, totalTrades: 20 } },
    {
      method: 'GET',
      path: '/admin/config*',
      data: {
        systemName: 'OAISS CHAIN',
        enableBlockChain: true,
        maxUploadSize: '50MB',
        sessionTimeout: 120,
        version: '1.1.0',
        enableCaptcha: true,
      },
    },
  ],
  REVIEWER: [
    { method: 'GET', path: '/user/profile*', data: { id: 4, username: 'reviewer001', realName: 'Reviewer 001', userType: 2 } },
    { method: 'GET', path: '/carbon/reports*', data: EMPTY_PAGE },
    { method: 'GET', path: '/reviewer/reports/pending*', data: EMPTY_PAGE },
    { method: 'GET', path: '/reviewer/history*', data: EMPTY_PAGE },
    {
      method: 'GET',
      path: '/reviewer/statistics*',
      data: { totalReviews: 0, approvedCount: 0, rejectedCount: 0, approvalRate: '0%' },
    },
    {
      method: 'GET',
      path: '/reviewer/info*',
      data: { name: 'reviewer', organization: 'QA Org', qualificationNo: 'RQ-TEST-001' },
    },
    { method: 'GET', path: '/reviewer/qualification/my*', data: [] },
  ],
  THIRD_PARTY: [
    { method: 'GET', path: '/user/profile*', data: { id: 5, username: 'thirdparty001', realName: 'Third Party 001', userType: 3 } },
    { method: 'GET', path: '/third-party/carbon-reports*', data: EMPTY_PAGE },
    { method: 'GET', path: '/third-party/statistics*', data: { totalReports: 30, pendingReports: 8, approvedReports: 20, rejectedReports: 2 } },
    {
      method: 'GET',
      path: '/third-party/org-info*',
      data: {
        orgName: 'Third Party Regulator',
        accessLevel: 'L2',
        address: 'Shanghai',
        contactPerson: 'thirdparty',
        contactPhone: '13800138000',
      },
    },
  ],
}
