import { type Page, expect } from '@playwright/test'

export class AuctionMarketPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/trading/market')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/trading\/market/)
  }

  async expectTabs(): Promise<void> {
    await expect(this.page.getByRole('tab', { name: '全部挂单' })).toBeVisible()
    await expect(this.page.getByRole('tab', { name: '我的挂单' })).toBeVisible()
    await expect(this.page.getByRole('tab', { name: '撮合结果' })).toBeVisible()
  }

  async expectOrderForm(): Promise<void> {
    await expect(this.page.getByRole('button', { name: /提交|下单/ })).toBeVisible()
  }
}
