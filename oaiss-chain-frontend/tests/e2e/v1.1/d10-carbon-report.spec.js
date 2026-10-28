import { test, expect } from '@playwright/test'
import { buildStorageState, loginViaToken } from '../fixtures/auth'

const MOCK_REPORTS = {
  content: [
    {
      id: 1,
      reportNo: 'RPT-2026-001',
      title: '2026 Q1 Carbon Emission Report',
      accountingPeriod: '2026-Q1',
      totalEmission: 1250.5,
      status: 0,
      statusText: '草稿',
      reviewerName: '',
      createdAt: '2026-05-16T08:00:00Z',
    },
    {
      id: 2,
      reportNo: 'RPT-2026-002',
      title: '2026 Q1 Power Generation Report',
      accountingPeriod: '2026-Q1',
      totalEmission: 3500.0,
      status: 1,
      statusText: '待审核',
      reviewerName: '审阅员A',
      createdAt: '2026-05-15T10:00:00Z',
    },
  ],
  totalElements: 2,
}

function mockCarbonApi(page, reports = MOCK_REPORTS) {
  page.route(/\/api\/v1\/carbon/, (route) => {
    const url = route.request().url()
    const method = route.request().method()
    if (url.includes('/my-reports') && method === 'GET') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data: reports }),
      })
    } else if (url.includes('/reports') && method === 'POST') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data: { id: 3, reportNo: 'RPT-2026-003' } }),
      })
    } else {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data: null }),
      })
    }
  })
}

test.describe('D10: Carbon Report Lifecycle', () => {
  test.describe('Report List', () => {
    test.use({ storageState: buildStorageState('enterprise') })

    test.beforeEach(async ({ page }) => {
      mockCarbonApi(page)
      await loginViaToken(page, 'enterprise')
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')
    })

    test('carbon upload page renders with breadcrumb', async ({ page }) => {
      await expect(page).toHaveURL(/\/enterprise\/carbon\/upload/)
      const breadcrumb = page.locator('.el-breadcrumb')
      await expect(breadcrumb).toBeVisible()
    })

    test('report table displays mock data', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper')
      await expect(tableBody).toBeVisible()
      await expect(tableBody.locator('tbody tr')).toHaveCount(2)
    })

    test('report data shows report number and title', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper')
      await expect(tableBody).toContainText('RPT-2026-001')
      await expect(tableBody).toContainText('2026 Q1 Carbon Emission Report')
    })

    test('report status renders as tag', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper')
      await expect(tableBody.locator('.el-tag').first()).toBeVisible()
    })

    test('draft report shows submit and delete buttons', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper')
      const firstRow = tableBody.locator('tbody tr').first()
      await expect(firstRow.getByRole('button', { name: /提交|submit/i })).toBeVisible()
      await expect(firstRow.getByRole('button', { name: /删除|delete/i })).toBeVisible()
    })

    test('pending review report does not show submit or delete buttons', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper')
      const secondRow = tableBody.locator('tbody tr').nth(1)
      await expect(secondRow.getByRole('button', { name: /提交|submit/i })).toHaveCount(0)
      await expect(secondRow.getByRole('button', { name: /删除|delete/i })).toHaveCount(0)
    })

    test('pagination shows correct total', async ({ page }) => {
      const pagination = page.locator('.el-pagination')
      await expect(pagination).toContainText('2')
    })

    test('search form has title and period inputs', async ({ page }) => {
      const form = page.locator('.search-form')
      await expect(form).toBeVisible()
      await expect(form.getByPlaceholder(/标题|title/i).first()).toBeVisible()
    })

    test('create report button exists', async ({ page }) => {
      await expect(page.getByRole('button', { name: /新建|新增|创建|create/i })).toBeVisible()
    })
  })

  test.describe('Create Report Dialog', () => {
    test.use({ storageState: buildStorageState('enterprise') })

    test.beforeEach(async ({ page }) => {
      mockCarbonApi(page)
      await loginViaToken(page, 'enterprise')
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')
    })

    test('opens create dialog on button click', async ({ page }) => {
      const createBtn = page.getByRole('button', { name: /新建|新增|创建|create/i })
      await createBtn.click()
      const dialog = page.locator('.el-dialog:visible')
      await expect(dialog).toBeVisible()
    })

    test('create dialog has form fields', async ({ page }) => {
      await page.getByRole('button', { name: /新建|新增|创建|create/i }).click()
      const dialog = page.locator('.el-dialog:visible')
      await expect(dialog.locator('input').first()).toBeVisible()
      await expect(dialog.locator('textarea').first()).toBeVisible()
    })

    test('create dialog has submit and cancel buttons', async ({ page }) => {
      await page.getByRole('button', { name: /新建|新增|创建|create/i }).click()
      const dialog = page.locator('.el-dialog:visible')
      await expect(dialog.getByRole('button', { name: /确定|创建|create|提交|submit/i })).toBeVisible()
      await expect(dialog.getByRole('button', { name: /取消|cancel/i })).toBeVisible()
    })
  })

  test.describe('Empty State', () => {
    test.use({ storageState: buildStorageState('enterprise') })

    test.beforeEach(async ({ page }) => {
      mockCarbonApi(page, { content: [], totalElements: 0 })
      await loginViaToken(page, 'enterprise')
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')
    })

    test('shows empty state when no reports', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper')
      await expect(tableBody).toBeVisible()
      await expect(tableBody.locator('.el-table__empty-text')).toBeVisible()
    })
  })

  test.describe('API Integration', () => {
    test.use({ storageState: buildStorageState('enterprise') })

    test('page calls my-reports endpoint on load', async ({ page }) => {
      const requests = []
      page.on('request', (req) => {
        if (req.url().includes('/api/v1/carbon')) {
          requests.push({ method: req.method(), url: req.url() })
        }
      })

      mockCarbonApi(page)
      await loginViaToken(page, 'enterprise')
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')

      const myReportReqs = requests.filter((r) => r.url.includes('/my-reports'))
      expect(myReportReqs.length).toBeGreaterThanOrEqual(1)
    })
  })
})
