import { type Page, expect } from '@playwright/test'

export class CreditScorePage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/credit/score')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/credit\/score/)
  }

  async expectScoreDisplay(): Promise<void> {
    await expect(this.page.getByText('当前评分').first()).toBeVisible()
  }

  async expectHistoryTable(): Promise<void> {
    await expect(this.page.getByRole('columnheader', { name: '事件类型' })).toBeVisible()
  }
}
