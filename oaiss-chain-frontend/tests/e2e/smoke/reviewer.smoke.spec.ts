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

    // 审核员叶子同为 rail: true，侧栏二级项为空；断言图标栏与顶栏页签。
    // 本轮新增「成交审核」——可疑成交转人工裁决，是审核职责的延伸。
    await layout.expectRailCount(5)
    await layout.expectTopTabCount(5)
    // 顶栏显示的是 TAB_LABEL 的文案，不是菜单项名：
    //   tab=verify 的菜单叫「认证管理」，但顶栏文案是「上链补救」
    //   tab=trade  共用同一页签，文案是「交易市场」
    await layout.expectTopTabs(['审核材料', '项目核证', '交易市场', '上链补救', '搜索查询'])
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
