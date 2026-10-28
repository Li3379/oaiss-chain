import { type Page, expect } from '@playwright/test'

export class LoginPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/login')
    await this.page.waitForLoadState('domcontentloaded')
  }

  async expectLoaded(): Promise<void> {
    const inputs = this.page.locator('.login-card .el-input input')
    await expect(inputs.nth(0)).toBeVisible()
    await expect(inputs.nth(1)).toBeVisible()
    await expect(this.page.locator('.submit-btn')).toBeVisible()
  }

  async login(username: string, password: string): Promise<void> {
    const inputs = this.page.locator('.login-card .el-input input')
    await inputs.nth(0).fill(username)
    await inputs.nth(1).fill(password)
    await this.page.locator('.submit-btn').click()
  }
}
