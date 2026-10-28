import { test, expect } from '@playwright/test'
import { buildStorageState, setupApiMock, loginViaToken } from './fixtures/auth'

test.describe('D7: 碳中和项目生命周期', () => {
  test.describe('企业端-项目列表', () => {
    test.use({ storageState: buildStorageState('enterprise') })

    test.beforeEach(async ({ page }) => {
      setupApiMock(page)
      await loginViaToken(page, 'enterprise')
      await page.goto('/enterprise/carbon-neutral/projects')
      await page.waitForLoadState('networkidle')
    })

    test('企业端页面渲染完整', async ({ page }) => {
      await expect(page).toHaveURL(/\/enterprise\/carbon-neutral/)
      await expect(page.locator('header')).toBeVisible()
    })

    test('项目列表表格存在', async ({ page }) => {
      await expect(page.getByRole('table').first()).toBeVisible()
    })

    test('新建项目按钮存在', async ({ page }) => {
      await expect(page.getByRole('button', { name: /新建|新增|创建/ })).toBeVisible()
    })
  })

  test.describe('管理员端-审核页面', () => {
    test.use({ storageState: buildStorageState('admin') })

    test.beforeEach(async ({ page }) => {
      setupApiMock(page)
      await loginViaToken(page, 'admin')
      await page.goto('/admin/system/carbon')
      await page.waitForLoadState('networkidle')
    })

    test('管理员端审核页面渲染', async ({ page }) => {
      await expect(page).toHaveURL(/\/admin\/system\/carbon/)
      await expect(page.locator('header')).toBeVisible()
    })
  })
})
