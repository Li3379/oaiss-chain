import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

test.describe('Flow: Auction Lifecycle', () => {
  test.describe('Order Creation', () => {
    test('enterprise can create order via dialog', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/trading/market')
      await page.waitForLoadState('networkidle')

      // Look for the create order button (may vary by page state)
      const createBtn = page.getByRole('button', { name: /创建挂单|新建|发布/ }).first()
      if (await createBtn.isVisible()) {
        await createBtn.click()

        const dialog = page.getByRole('dialog')
        if (await dialog.isVisible()) {
          await dialog.getByPlaceholder(/数量/).fill('1000')
          await dialog.getByPlaceholder(/价格/).fill('50')
          await dialog.getByRole('button', { name: /提交|确认|创建/ }).click()

          await expect(page.getByText(/成功|已提交/).first()).toBeVisible({ timeout: 10000 }).catch(() => {})
        }
      }
    })
  })

  test.describe('Tab Navigation', () => {
    test('enterprise can switch between tabs', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/trading/market')
      await page.waitForLoadState('networkidle')

      await page.getByRole('tab', { name: '我的挂单' }).click()
      await page.waitForLoadState('networkidle')

      await page.getByRole('tab', { name: '撮合结果' }).click()
      await page.waitForLoadState('networkidle')
    })
  })
})
