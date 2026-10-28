import { test, expect } from '@playwright/test'
import { loginViaApi } from '../fixtures/auth'
import { setupSmokeMock } from '../fixtures/api-mock'
import { Layout } from '../fixtures/page-objects/Layout'
import { VerifyListPage } from '../fixtures/page-objects/VerifyListPage'

test.describe('Role: Admin - Verify Feature Smoke', () => {
  test.beforeEach(async ({ page }) => {
    setupSmokeMock(page, 'ADMIN')
    await loginViaApi(page, 'admin', 'admin123')
  })

  test('sidebar menu includes certification', async ({ page }) => {
    await page.goto('/admin/verify/list', { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(500)
    await page.waitForLoadState('domcontentloaded')
    await expect(page).toHaveURL(/\/admin\/verify\/list/)
    const layout = new Layout(page)
    await layout.expectSidebar(['认证列表'])
  })

  test('user info displayed', async ({ page }) => {
    await page.goto('/admin/verify/list')
    await page.waitForLoadState('domcontentloaded')
    await expect(page).toHaveURL(/\/admin\/verify\/list/)
    const layout = new Layout(page)
    await layout.expectUserInfo('admin', '管理员')
  })

  test.describe('Verify List', () => {
    test('page loads with table', async ({ page }) => {
      const po = new VerifyListPage(page)
      await po.goto()
      await po.expectLoaded()
      await po.expectTable()
    })
  })
})
