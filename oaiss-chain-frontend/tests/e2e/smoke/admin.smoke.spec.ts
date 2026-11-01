import { test, expect } from '@playwright/test'
import { loginViaToken } from '../fixtures/auth'
import { setupSmokeMock } from '../fixtures/api-mock'
import { Layout } from '../fixtures/page-objects/Layout'
import { AdminUsersPage } from '../fixtures/page-objects/AdminUsersPage'

test.describe('Role: Admin - Smoke', () => {
  test.beforeEach(async ({ page }) => {
    setupSmokeMock(page, 'ADMIN')
    await loginViaToken(page, 'ADMIN')
  })

  test('sidebar menu complete', async ({ page }) => {
    await page.goto('/admin/system/users')
    await page.waitForLoadState('networkidle')
    const layout = new Layout(page)
    // 菜单随功能演进：管理员新增「链上管理」(运维对账/死信) 与
    // 「成交审核」(审核员卡单时的人工兜底)。
    await layout.expectSidebar(['系统管理', '数据管理', '撮合控制台', '成交审核', '链上管理', '证书管理', '搜索查询'])
    await layout.expectRailCount(6)
  })

  test('user info displayed', async ({ page }) => {
    await page.goto('/admin/system/users')
    await page.waitForLoadState('networkidle')
    const layout = new Layout(page)
    await layout.expectUserInfo('admin', '管理员')
  })

  test.describe('User Management', () => {
    test('page loads with table', async ({ page }) => {
      const po = new AdminUsersPage(page)
      await po.goto()
      await po.expectLoaded()
      await po.expectUserTable()
    })

    test('breadcrumb correct', async ({ page }) => {
      await page.goto('/admin/system/users')
      await page.waitForLoadState('networkidle')
      const layout = new Layout(page)
      await layout.expectBreadcrumb('系统管理', '用户管理')
    })
  })

  test.describe('Carbon Report Management', () => {
    test('page loads', async ({ page }) => {
      await page.goto('/admin/system/carbon')
      await page.waitForLoadState('networkidle')
      await expect(page).toHaveURL(/\/admin\/system\/carbon/)
    })
  })

  test.describe('System Config', () => {
    test('page loads', async ({ page }) => {
      await page.goto('/admin/system/config')
      await page.waitForLoadState('networkidle')
      await expect(page).toHaveURL(/\/admin\/system\/config/)
    })
  })

  test.describe('Statistics', () => {
    test('page loads', async ({ page }) => {
      await page.goto('/admin/data/statistics')
      await page.waitForLoadState('networkidle')
      await expect(page).toHaveURL(/\/admin\/data\/statistics/)
    })
  })
})
