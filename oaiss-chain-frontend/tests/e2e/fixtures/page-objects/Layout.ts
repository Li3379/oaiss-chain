import { type Page, expect } from '@playwright/test'

export class Layout {
  constructor(private page: Page) {}

  async expectSidebar(expectedItems: string[]): Promise<void> {
    for (const item of expectedItems) {
      await expect(this.page.getByRole('menuitem', { name: item })).toBeVisible()
    }
  }

  async expectUserInfo(username: string, roleLabel: string): Promise<void> {
    const header = this.page.locator('header')
    await expect(header.getByText(username, { exact: true })).toBeVisible()
    await expect(header.getByText(roleLabel)).toBeVisible()
  }

  async expectBreadcrumb(...segments: string[]): Promise<void> {
    // Element Plus breadcrumb accessible name follows locale; avoid hard-coding one language label.
    const nav = this.page.locator('.el-breadcrumb').first()
    await expect(nav).toBeVisible()
    for (const segment of segments) {
      await expect(nav).toContainText(segment)
    }
  }
}
