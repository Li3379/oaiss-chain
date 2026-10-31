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

  test('admin sidebar no longer exposes the duplicate certification page', async ({ page }) => {
    // 2026-10-28: 管理员端「认证列表」与审核员「上链补救」共用同一组件，已删除该菜单项与路由。
    await page.goto('/admin/system/users', { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(500)
    await page.waitForLoadState('domcontentloaded')
    const layout = new Layout(page)
    await layout.expectSidebar(['证书管理'])
    await expect(page.getByRole('menuitem', { name: '认证管理' })).toHaveCount(0)
  })

  test('admin can still reach the remediation page as fallback', async ({ page }) => {
    await page.goto('/auditor/verify/list', { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('domcontentloaded')
    await expect(page).toHaveURL(/\/auditor\/verify\/list/)
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
