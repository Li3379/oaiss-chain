import { type Page, expect } from '@playwright/test'

export class AdminUsersPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/admin/system/users')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/admin\/system\/users/)
  }

  async expectUserTable(): Promise<void> {
    await expect(this.page.getByRole('columnheader', { name: /用户名|账号/ })).toBeVisible()
    await expect(this.page.getByRole('columnheader', { name: /状态/ })).toBeVisible()
  }

  async expectStatusFilter(): Promise<void> {
    await expect(this.page.getByRole('combobox').or(this.page.getByPlaceholder(/筛选|搜索/))).toBeVisible()
  }
}
