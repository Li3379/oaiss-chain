import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

test.describe('Flow: Carbon Coin Lifecycle', () => {
  test.describe('Account View', () => {
    test('enterprise can view balance', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-coin/account')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/enterprise\/carbon-coin\/account/)
      await expect(page.getByText('账户余额').first()).toBeVisible()
    })

    test('enterprise can view transaction history', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-coin/account')
      await page.waitForLoadState('networkidle')

      await expect(page.getByText('交易记录').first()).toBeVisible()
    })
  })
})
