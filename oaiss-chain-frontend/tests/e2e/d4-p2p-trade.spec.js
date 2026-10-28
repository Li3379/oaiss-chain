import { test, expect } from '@playwright/test'
import { buildStorageState, setupApiMock, loginViaToken } from './fixtures/auth'

test.use({ storageState: buildStorageState('enterprise') })

test.describe('D4: P2P交易', () => {
  test.beforeEach(async ({ page }) => {
    setupApiMock(page)
    await loginViaToken(page, 'enterprise')
    await page.goto('/enterprise/trading/p2p')
    await page.waitForLoadState('networkidle')
  })

  test('页面渲染完整', async ({ page }) => {
    await expect(page).toHaveURL(/\/enterprise\/trading\/p2p/)
  })

  test('面包屑导航正确', async ({ page }) => {
    const breadcrumb = page.getByRole('navigation', { name: 'Breadcrumb' })
    await expect(breadcrumb).toContainText('碳交易')
    await expect(breadcrumb).toContainText('P2P交易')
  })

  test('搜索表单存在', async ({ page }) => {
    await expect(page.getByPlaceholder('请输入名称')).toBeVisible()
    await expect(page.getByText('身份', { exact: true })).toBeVisible()
    await expect(page.getByPlaceholder('请输入碳交易订单号')).toBeVisible()
    await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(page.getByRole('button', { name: '创建P2P交易' })).toBeVisible()
  })

  test('表格列头正确', async ({ page }) => {
    const headers = ['序号', '交易编号', '买方名称', '卖方名称', '交易数量', '单价', '总金额', '状态', '创建时间', '操作']
    for (const h of headers) {
      await expect(page.getByRole('columnheader', { name: h })).toBeVisible()
    }
  })

  test('空数据状态显示 No Data', async ({ page }) => {
    await expect(page.getByText('暂无P2P交易记录')).toBeVisible()
  })
})
