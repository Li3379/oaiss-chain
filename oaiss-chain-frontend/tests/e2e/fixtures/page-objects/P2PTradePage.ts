import { type Page, expect } from '@playwright/test'

export class P2PTradePage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/trading/p2p')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/trading\/p2p/)
    await expect(this.page.getByRole('button', { name: '创建P2P交易' })).toBeVisible()
  }
}
