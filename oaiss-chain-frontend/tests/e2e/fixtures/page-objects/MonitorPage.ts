import { type Page, expect } from '@playwright/test'

export class MonitorPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/third-party/monitor')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/third-party\/monitor/)
  }

  async expectOrgInfo(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: '监管面板' })).toBeVisible()
  }

  async expectStatistics(): Promise<void> {
    await expect(this.page.getByText('数据统计').first()).toBeVisible()
  }
}
