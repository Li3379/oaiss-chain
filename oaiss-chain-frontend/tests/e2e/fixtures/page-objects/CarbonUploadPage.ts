import { type Page, expect } from '@playwright/test'

export class CarbonUploadPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/carbon/upload')
    await this.page.waitForLoadState('domcontentloaded')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/carbon\/upload/)
    await expect(this.page.locator('.section-card').first()).toBeVisible()
    await expect(this.page.locator('.el-button').filter({ hasText: /创建|Create/ }).first()).toBeVisible()
  }

  async expectTableHeaders(): Promise<void> {
    await expect(this.page.locator('.el-table')).toBeVisible()
    await expect(this.page.getByRole('columnheader').first()).toBeVisible()
  }

  async expectEmptyState(): Promise<void> {
    await expect(this.page.locator('.el-empty, .el-table__empty-block')).toBeVisible()
  }

  async expectSearchForm(): Promise<void> {
    await expect(this.page.locator('.search-form')).toBeVisible()
    await expect(this.page.locator('.search-form .el-input').first()).toBeVisible()
    await expect(this.page.locator('.search-form .el-button').filter({ hasText: /查询|Search/ }).first()).toBeVisible()
  }
}
