import { test, expect } from '@playwright/test'
import { buildStorageState, setupApiMock, loginViaToken } from './fixtures/auth'

test.use({ storageState: buildStorageState('admin') })

test.describe('D5: 管理员-用户管理', () => {
  test.beforeEach(async ({ page }) => {
    setupApiMock(page)
    await loginViaToken(page, 'admin')
    await page.goto('/admin/system/users')
    await page.waitForLoadState('networkidle')
  })

  test('页面渲染完整', async ({ page }) => {
    await expect(page).toHaveURL(/\/admin\/system\/users/)
    await expect(page.getByText('admin')).toBeVisible()
    await expect(page.locator('header').getByText('管理员')).toBeVisible()
  })

  test('面包屑导航正确', async ({ page }) => {
    const breadcrumb = page.getByRole('navigation', { name: 'Breadcrumb' })
    await expect(breadcrumb).toContainText('系统管理')
    await expect(breadcrumb).toContainText('用户管理')
  })

  test('管理员侧边栏菜单完整', async ({ page }) => {
    await expect(page.getByRole('menuitem', { name: '用户管理' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: '碳核算管理' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: '系统配置' })).toBeVisible()
  })

  test('搜索表单存在', async ({ page }) => {
    await expect(page.getByLabel('用户类型')).toBeVisible()
    await expect(page.getByLabel('状态')).toBeVisible()
    await expect(page.getByRole('button', { name: '查询' })).toBeVisible()
  })

  test('表格列头正确', async ({ page }) => {
    const headers = ['用户名', '邮箱', '用户类型', '状态', '创建时间', '操作']
    for (const h of headers) {
      await expect(page.getByRole('columnheader', { name: h })).toBeVisible()
    }
  })

  test('空数据状态显示 No Data', async ({ page }) => {
    await expect(page.getByText('No Data')).toBeVisible()
    await expect(page.getByText('Total 0')).toBeVisible()
  })
})
