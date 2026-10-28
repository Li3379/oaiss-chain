import { type Page, expect } from '@playwright/test'

export class CarbonCoinPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/carbon-coin/account')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/carbon-coin\/account/)
  }

  async expectBalance(): Promise<void> {
    await expect(this.page.getByText('账户余额').first()).toBeVisible()
  }

  async expectActionButtons(): Promise<void> {
    await expect(this.page.getByRole('button', { name: /充值|转账/ })).toBeVisible()
  }
}
