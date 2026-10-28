import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

test.describe('Flow: Credit Score Lifecycle', () => {
  test.describe('Enterprise View', () => {
    test('enterprise can view own score', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/credit/score')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/enterprise\/credit\/score/)
      await expect(page.getByText('当前评分').first()).toBeVisible()
    })

    test('enterprise can view score history', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/credit/score')
      await page.waitForLoadState('networkidle')

      await expect(page.getByRole('columnheader', { name: '事件类型' })).toBeVisible()
      await expect(page.getByRole('columnheader', { name: '变化值' })).toBeVisible()
    })
  })

  test.describe('Admin Actions', () => {
    test('admin can view statistics', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.admin.username, TEST_USERS.admin.password)
      await page.goto('/admin/data/statistics')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/admin\/data\/statistics/)
    })
  })
})
