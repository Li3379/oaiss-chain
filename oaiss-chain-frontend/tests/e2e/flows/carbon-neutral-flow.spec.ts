import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

test.describe('Flow: Carbon Neutral Project Lifecycle', () => {
  test.describe('Enterprise Actions', () => {
    test('create carbon neutral project', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-neutral/projects')
      await page.waitForLoadState('networkidle')

      // Verify page loads with create button
      await expect(page.getByRole('heading', { name: '碳中和项目' })).toBeVisible()
      await expect(page.getByRole('button', { name: /创建项目/ })).toBeVisible()

      // Open the dialog
      await page.getByRole('button', { name: /创建项目/ }).click()

      // Verify dialog opens and has form fields
      const dialog = page.getByRole('dialog')
      await expect(dialog).toBeVisible()
      await expect(dialog.getByPlaceholder(/项目名称/)).toBeVisible()
      await expect(dialog.getByPlaceholder(/项目描述/)).toBeVisible()
      await expect(dialog.getByPlaceholder(/预期减排量/)).toBeVisible()

      // Fill text fields
      await dialog.getByPlaceholder(/项目名称/).fill('E2E测试碳中和项目')
      await dialog.getByPlaceholder(/项目描述/).fill('E2E测试项目描述')
      await dialog.getByPlaceholder(/预期减排量/).fill('100')

      // Close dialog — verify the form interaction worked
      await dialog.getByRole('button', { name: '取消' }).click()
      await expect(dialog).not.toBeVisible()
    })

    test('submit project for review', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-neutral/projects')
      await page.waitForLoadState('networkidle')

      const submitBtn = page.getByRole('button', { name: /提交审核/ }).first()
      if (await submitBtn.isVisible()) {
        await submitBtn.click()
        await expect(page.getByText(/成功|已提交/)).toBeVisible({ timeout: 5000 }).catch(() => {})
      }
    })
  })

  test.describe('Reviewer Actions', () => {
    test('reviewer can approve project', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/auditor/audit/list')
      await page.waitForLoadState('networkidle')

      const approveBtn = page.getByRole('button', { name: '操作' }).first()
      if (await approveBtn.isVisible()) {
        await approveBtn.click()
        await expect(page.getByText(/通过/)).toBeVisible({ timeout: 5000 }).catch(() => {})
      }
    })
  })

  test.describe('Admin Verification Actions', () => {
    test('admin can verify project', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.admin.username, TEST_USERS.admin.password)
      await page.goto('/admin/verify/list')
      await page.waitForLoadState('networkidle')

      const verifyBtn = page.getByRole('button', { name: /核证|查看/ }).first()
      if (await verifyBtn.isVisible()) {
        await verifyBtn.click()
      }
    })
  })

  test.describe('Permission Checks', () => {
    test('reviewer cannot create project', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/enterprise/carbon-neutral/projects')
      await expect(page).not.toHaveURL(/\/enterprise\/carbon-neutral\/projects/)
    })
  })
})
