import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

test.describe('Flow: Third Party Monitor', () => {
  test.describe('Monitor Panel', () => {
    test('third party can view monitor page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/third-party/monitor')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/third-party\/monitor/)
      await expect(page.getByRole('heading', { name: '监管面板' })).toBeVisible()
    })

    test('third party can view statistics', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/third-party/monitor')
      await page.waitForLoadState('networkidle')

      await expect(page.getByText('数据统计').first()).toBeVisible()
    })

    test('third party can view carbon report table', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/third-party/monitor')
      await page.waitForLoadState('networkidle')

      await expect(page.getByRole('columnheader', { name: '报告编号' })).toBeVisible()
    })
  })

  test.describe('Permission Checks', () => {
    test('third party cannot access enterprise pages', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/enterprise/carbon/upload')
      await expect(page).not.toHaveURL(/\/enterprise\/carbon\/upload/)
    })
  })
})
