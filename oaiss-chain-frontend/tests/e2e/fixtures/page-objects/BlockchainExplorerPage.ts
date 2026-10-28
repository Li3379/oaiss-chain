import { type Page, expect } from '@playwright/test'

export class BlockchainExplorerPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/blockchain/browser')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/blockchain\/browser/)
    await expect(this.page.locator('header')).toBeVisible()
  }

  async expectBlocksTable(): Promise<void> {
    await expect(this.page.locator('.el-table__body-wrapper').first()).toBeVisible()
  }

  async switchToTransactionsTab(): Promise<void> {
    const tabs = this.page.locator('.el-tabs__item')
    await expect(tabs).toHaveCount(2, { timeout: 5000 })
    await tabs.nth(1).click()
  }

  async expectTransactionsTable(): Promise<void> {
    const tableBody = this.page.locator(
      '.el-tab-pane:not([style*="display: none"]) .el-table__body-wrapper',
    ).first()
    await expect(tableBody).toBeVisible()
  }

  async expectEmptyState(): Promise<void> {
    await expect(this.page.locator('.el-table__empty-text').first()).toBeVisible()
  }
}
