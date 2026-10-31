import { test, expect } from '@playwright/test'
import { loginViaToken } from '../fixtures/auth'
import { setupSmokeMock } from '../fixtures/api-mock'
import { Layout } from '../fixtures/page-objects/Layout'
import { AuditListPage } from '../fixtures/page-objects/AuditListPage'

test.describe('Role: Reviewer - Smoke', () => {
  test.beforeEach(async ({ page }) => {
    setupSmokeMock(page, 'REVIEWER')
    await loginViaToken(page, 'REVIEWER')
  })

  test('sidebar menu complete', async ({ page }) => {
    await page.goto('/auditor/audit/list')
    await page.waitForLoadState('networkidle')
    const layout = new Layout(page)
    await layout.expectSidebar(['审核材料', '项目核证', '认证管理', '搜索查询'])
    await layout.expectRailCount(4)
  })

  test('user info displayed', async ({ page }) => {
    await page.goto('/auditor/audit/list')
    await page.waitForLoadState('networkidle')
    const layout = new Layout(page)
    await layout.expectUserInfo('reviewer001', '审核员')
  })

  test.describe('Audit List', () => {
    test('page loads with table', async ({ page }) => {
      const po = new AuditListPage(page)
      await po.goto()
      await po.expectLoaded()
      await po.expectAuditTable()
    })

    test('action buttons exist', async ({ page }) => {
      const po = new AuditListPage(page)
      await po.goto()
      await po.expectActionButtons()
    })
  })
})
