import { test, expect } from '@playwright/test'
import { buildStorageState, setupApiMock, loginViaToken } from './fixtures/auth'

test.use({ storageState: buildStorageState('enterprise') })

test.describe('D8: 企业-信用评分查询', () => {
  test.beforeEach(async ({ page }) => {
    setupApiMock(page)
    await loginViaToken(page, 'enterprise')
    await page.goto('/enterprise/credit/score')
    await page.waitForLoadState('networkidle')
  })

  test('页面渲染完整', async ({ page }) => {
    await expect(page).toHaveURL(/\/enterprise\/credit\/score/)
    await expect(page.locator('header')).toBeVisible()
  })

  test('信用评分区域存在', async ({ page }) => {
    await expect(page.getByText('当前评分')).toBeVisible()
  })

  test('评分明细表格存在', async ({ page }) => {
    await expect(page.getByRole('table').first()).toBeVisible()
  })
})
