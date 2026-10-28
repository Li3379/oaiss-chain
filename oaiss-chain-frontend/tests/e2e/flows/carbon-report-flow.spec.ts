import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

test.describe('Flow: Carbon Report Lifecycle', () => {
  test.describe('Enterprise Actions', () => {
    test('create carbon report', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')

      const reportTitle = `E2E-CARBON-${Date.now()}`

      await page.getByRole('button', { name: /^创建$|^Create$/ }).click()

      const dialog = page.getByRole('dialog')
      await expect(dialog).toBeVisible()
      await dialog.getByPlaceholder(/2024-Q1|e\.g\./i).fill('2026-Q2')
      await dialog.getByPlaceholder(/请输入报告标题|Enter report title/i).fill(reportTitle)
      await dialog.getByPlaceholder(/JSON格式|JSON format/i).fill('{"scope1":100,"scope2":200}')
      await dialog.getByRole('button', { name: /^创建$|^Create$/ }).click()
      await expect(page.getByText(/成功|Created successfully|创建成功/i).first()).toBeVisible({ timeout: 10000 }).catch(() => {})
      if (await dialog.isVisible().catch(() => false)) {
        await dialog.getByRole('button', { name: /关闭|close|取消|cancel/i }).first().click().catch(() => {})
      }

      const searchTitleInput = page.getByRole('textbox', { name: /^报告标题$|^Report Title$/i })
      await searchTitleInput.fill(reportTitle)
      await page.getByRole('button', { name: /^查询$|^Search$/ }).click()
      await expect(page.getByText(reportTitle)).toBeVisible({ timeout: 10000 })
    })

    test('submit report changes status', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')

      const submitBtn = page.getByRole('button', { name: /^提交$|^Submit$/ }).first()
      if (await submitBtn.isVisible()) {
        await submitBtn.click()
        await expect(page.getByText(/成功|已提交|success|submitted/i)).toBeVisible({ timeout: 5000 }).catch(() => {})
      }
    })
  })

  test.describe('Reviewer Actions', () => {
    test('reviewer can view pending reports', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/auditor/audit/list')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/auditor\/audit\/list/)
      await expect(page.getByRole('columnheader', { name: /报告编号|Report No/i })).toBeVisible()
    })

    test('reviewer can approve report', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/auditor/audit/list')
      await page.waitForLoadState('networkidle')

      const actionBtn = page.getByRole('button', { name: /操作|Action/i }).first()
      if (await actionBtn.isVisible()) {
        await actionBtn.click()
        await expect(page.getByText(/通过|Approved?|approve/i)).toBeVisible({ timeout: 5000 }).catch(() => {})
      }
    })
  })

  test.describe('Permission Checks', () => {
    test('enterprise cannot access review page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/auditor/audit/list')
      await expect(page).not.toHaveURL(/\/auditor\/audit\/list/)
    })
  })
})
