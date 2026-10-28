import { test, expect } from '@playwright/test'
import { buildStorageState, setupApiMock, loginViaToken } from './fixtures/auth'

test.use({ storageState: buildStorageState('thirdParty') })

test.describe('D6: 第三方-碳监测', () => {
  test.beforeEach(async ({ page }) => {
    setupApiMock(page)
    await loginViaToken(page, 'thirdParty')
    await page.goto('/third-party/monitor')
    await page.waitForLoadState('networkidle')
  })

  test('页面渲染完整', async ({ page }) => {
    await expect(page).toHaveURL(/\/third-party\/monitor/)
    await expect(page.locator('header')).toBeVisible()
  })

  test('侧边栏菜单包含监控菜单项', async ({ page }) => {
    await expect(page.getByRole('menuitem', { name: '监管面板' })).toBeVisible()
  })

  test('统计数据区域存在', async ({ page }) => {
    await expect(page.getByText('总报告数')).toBeVisible()
  })

  test('报告列表表格存在', async ({ page }) => {
    await expect(page.getByRole('table').first()).toBeVisible()
  })
})
