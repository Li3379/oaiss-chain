import { test, expect } from '@playwright/test'
import { buildStorageState, setupApiMock, loginViaToken } from './fixtures/auth'

const authStorage = buildStorageState('enterprise')

test.use({ storageState: authStorage })

test.describe('D2: 碳核算-上传审核', () => {
  test.beforeEach(async ({ page }) => {
    setupApiMock(page)
    await loginViaToken(page, 'enterprise')
    await page.goto('/enterprise/carbon/upload')
    await page.waitForLoadState('networkidle')
  })

  test('页面渲染完整', async ({ page }) => {
    await expect(page).toHaveURL(/\/enterprise\/carbon\/upload/)
    await expect(page.getByText('testuser')).toBeVisible()
    await expect(page.locator('header').getByText('企业用户')).toBeVisible()
  })

  test('面包屑导航正确', async ({ page }) => {
    await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).toContainText('碳核算')
    await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).toContainText('上传审核')
  })

  test('搜索表单存在', async ({ page }) => {
    await expect(page.getByPlaceholder('请输入报告标题')).toBeVisible()
    await expect(page.getByPlaceholder('请输入核算周期')).toBeVisible()
    await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(page.getByRole('button', { name: '创建报告' })).toBeVisible()
  })

  test('表格列头正确', async ({ page }) => {
    const headers = ['报告编号', '报告标题', '核算周期', '总排放量(tCO2e)', '状态', '审核人', '创建时间', '操作']
    for (const h of headers) {
      await expect(page.getByRole('columnheader', { name: h })).toBeVisible()
    }
  })

  test('空数据状态显示 No Data', async ({ page }) => {
    await expect(page.getByText('暂无碳核算报告')).toBeVisible()
  })

  test('侧边栏菜单完整', async ({ page }) => {
    const menuItems = ['碳核算', 'P2P订单管理', '碳交易', '本公司信息', '信誉评分', '碳币账户', '区块链', '碳中和', '个人中心']
    for (const item of menuItems) {
      await expect(page.getByRole('menuitem', { name: item })).toBeVisible()
    }
  })
})
