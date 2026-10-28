import { type Page, expect } from '@playwright/test'

export class AuditListPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/auditor/audit/list')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/auditor\/audit\/list/)
  }

  async expectAuditTable(): Promise<void> {
    const headers = ['报告编号', '企业名称', '报告标题', '总排放量', '状态', '创建时间', '操作']
    for (const h of headers) {
      await expect(this.page.getByRole('columnheader', { name: h })).toBeVisible()
    }
  }

  async expectActionButtons(): Promise<void> {
    await expect(this.page.getByRole('columnheader', { name: '操作' })).toBeVisible()
  }
}
