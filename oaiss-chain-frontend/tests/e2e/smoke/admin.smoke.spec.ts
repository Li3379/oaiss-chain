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

    // 管理端所有叶子都是 rail: true，侧栏（rail === false 的二级项）本来就是空的，
    // 所以不能断言 role=menuitem——那样永远失败。真正可见的是两处：
    //   1. 72px 图标栏（7 个入口：「链上管理」+ 本轮新增的「区块链」浏览器）
    //   2. 顶栏一级页签（按 tab 去重：撮合控制台与成交审核共用 trade，只出现一次；
    //      区块浏览器与链上管理共用 chain，不新增页签）
    await layout.expectRailCount(7)
    await layout.expectTopTabCount(6)
    await layout.expectTopTabs(['系统管理', '数据管理', '交易市场', '链上管理', '证书管理', '搜索查询'])
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

  test.describe('Blockchain Browser', () => {
    test('page loads', async ({ page }) => {
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')
      await expect(page).toHaveURL(/\/enterprise\/blockchain\/browser/)
    })
  })
})
