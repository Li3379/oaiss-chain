import { type Page, expect } from '@playwright/test'

export class CarbonNeutralPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/carbon-neutral/projects')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/carbon-neutral\/projects/)
    await expect(this.page.getByRole('button', { name: /创建项目/ })).toBeVisible()
  }

  async expectSearchForm(): Promise<void> {
    await expect(this.page.getByPlaceholder(/项目名称|搜索/)).toBeVisible()
  }
}
