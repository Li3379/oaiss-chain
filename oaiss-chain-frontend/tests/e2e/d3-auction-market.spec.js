import { test, expect } from '@playwright/test'
import { buildStorageState, setupApiMock, loginViaToken } from './fixtures/auth'

test.use({ storageState: buildStorageState('enterprise') })

test.describe('D3: 双向拍卖', () => {
  test.beforeEach(async ({ page }) => {
    setupApiMock(page)
    await loginViaToken(page, 'enterprise')
    await page.goto('/enterprise/trading/market')
    await page.waitForLoadState('networkidle')
  })

  test('页面渲染完整', async ({ page }) => {
    await expect(page).toHaveURL(/\/enterprise\/trading\/market/)
  })

  test('面包屑导航正确', async ({ page }) => {
    const breadcrumb = page.getByRole('navigation', { name: 'Breadcrumb' })
    await expect(breadcrumb).toContainText('碳交易')
    await expect(breadcrumb).toContainText('双向拍卖')
  })

  test('Tab 切换存在', async ({ page }) => {
    await expect(page.getByRole('tab', { name: '全部挂单' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '全部挂单' })).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByRole('tab', { name: '我的挂单' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '撮合结果' })).toBeVisible()
  })

  test('表格列头正确', async ({ page }) => {
    const headers = ['序号', '买卖方向', '数量（吨）', '价格（元/吨）', '状态', '创建时间']
    for (const h of headers) {
      await expect(page.getByRole('columnheader', { name: h })).toBeVisible()
    }
  })

  test('搜索和创建按钮存在', async ({ page }) => {
    await expect(page.getByPlaceholder('请输入关键字')).toBeVisible()
    await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(page.getByRole('button', { name: '创建订单' })).toBeVisible()
  })

  test('空数据状态显示 No Data', async ({ page }) => {
    await expect(page.getByText('No Data')).toBeVisible()
    await expect(page.getByText('Total 0')).toBeVisible()
  })
})
