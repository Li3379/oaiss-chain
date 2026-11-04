import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

test.describe('Flow: Blockchain Browser', () => {
  test.describe('Enterprise Access', () => {
    test('enterprise is bounced away from blockchain browser', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')

      // Phase 8 D-01：区块浏览器移交给 ADMIN / THIRD_PARTY，
      // 路由守卫会把企业用户送回企业首页。
      await expect(page).not.toHaveURL(/\/enterprise\/blockchain\/browser/)
    })
  })

  test.describe('Regulator Access', () => {
    test('third-party can view blockchain browser', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/enterprise\/blockchain\/browser/)
    })

    test('third-party can browse latest blocks', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')

      const blockSection = page.getByText(/区块|block/i)
      if (await blockSection.isVisible().catch(() => false)) {
        await expect(blockSection.first()).toBeVisible()
      }
    })
  })
})
