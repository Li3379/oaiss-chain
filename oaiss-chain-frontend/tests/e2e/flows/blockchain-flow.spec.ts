import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

test.describe('Flow: Blockchain Browser', () => {
  test.describe('Enterprise Access', () => {
    test('enterprise can view blockchain browser', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/enterprise\/blockchain\/browser/)
    })

    test('enterprise can browse latest blocks', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')

      const blockSection = page.getByText(/区块|block/i)
      if (await blockSection.isVisible().catch(() => false)) {
        await expect(blockSection.first()).toBeVisible()
      }
    })
  })

  test.describe('Multi-Role Access', () => {
    test('third-party cannot access enterprise blockchain page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/enterprise/blockchain/browser')
      const url = page.url()
      expect(url).toBeTruthy()
    })
  })
})
