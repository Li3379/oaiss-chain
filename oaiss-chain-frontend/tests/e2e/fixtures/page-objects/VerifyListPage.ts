import { type Page, expect } from '@playwright/test'

export class VerifyListPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/admin/verify/list')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/admin\/verify\/list/)
  }

  async expectTable(): Promise<void> {
    await expect(this.page.getByRole('columnheader', { name: '报告编号' })).toBeVisible()
    await expect(this.page.getByRole('columnheader', { name: '企业名称' })).toBeVisible()
    await expect(this.page.getByRole('columnheader', { name: '状态' })).toBeVisible()
  }
}
