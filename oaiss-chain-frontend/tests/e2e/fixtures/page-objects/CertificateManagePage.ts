import { type Page, expect } from '@playwright/test'

export class CertificateManagePage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/admin/certificates')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/admin\/certificates/)
    // Wait for the el-tabs to render
    await expect(this.page.locator('.el-tabs')).toBeVisible()
  }

  async expectTabs(): Promise<void> {
    const tabs = this.page.getByRole('tab')
    const count = await tabs.count()
    expect(count).toBeGreaterThanOrEqual(2)
  }

  async selectEnterpriseAdmissionTab(): Promise<void> {
    const tab = this.page.getByRole('tab', { name: /准入|Admission/ })
    if (await tab.isVisible()) {
      await tab.click()
    }
  }

  async selectReviewerQualificationTab(): Promise<void> {
    const tab = this.page.getByRole('tab', { name: /资格|Qualification/ })
    if (await tab.isVisible()) {
      await tab.click()
    }
  }

  async expectCertificateList(): Promise<void> {
    // Wait for el-table to render (may be empty but table should exist)
    const table = this.page.locator('.el-table')
    await expect(table.first()).toBeVisible()
  }

  async clickIssueCertificate(): Promise<void> {
    const issueBtn = this.page.getByRole('button', { name: /签发|Issue|颁发/ })
    await issueBtn.click()
  }

  async expectIssueDialog(): Promise<void> {
    const dialog = this.page.locator('.el-dialog')
    await expect(dialog).toBeVisible()
  }

  async fillEnterpriseIdInDialog(id: string): Promise<void> {
    const input = this.page.locator('.el-dialog input')
    await input.fill(id)
  }

  async fillReviewerIdInDialog(id: string): Promise<void> {
    const input = this.page.locator('.el-dialog input')
    await input.fill(id)
  }

  async confirmIssue(): Promise<void> {
    // The dialog has a confirm button in the footer
    const confirmBtn = this.page.locator('.el-dialog__footer').getByRole('button', { name: /确认|Confirm|确定/ })
    await confirmBtn.click()
  }

  async clickRevokeCertificate(rowIdentifier: string): Promise<void> {
    // Find the table row containing the identifier and click its revoke button
    const row = this.page.locator('.el-table__body-wrapper tr').filter({ hasText: rowIdentifier })
    const revokeBtn = row.getByRole('button', { name: /吊销|Revoke/ })
    if (await revokeBtn.isVisible()) {
      await revokeBtn.click()
    }
  }

  async confirmRevoke(): Promise<void> {
    // ElMessageBox confirmation dialog
    const confirmBtn = this.page.locator('.el-message-box').getByRole('button', { name: /确认|Confirm|确定/ })
    await confirmBtn.click()
  }

  async getCertificateStatus(rowIdentifier: string): Promise<string | null> {
    const row = this.page.locator('.el-table__body-wrapper tr').filter({ hasText: rowIdentifier })
    const tag = row.locator('.el-tag').first()
    if (await tag.isVisible().catch(() => false)) {
      return await tag.textContent()
    }
    return null
  }

  async getTableRowCount(): Promise<number> {
    const rows = this.page.locator('.el-table__body-wrapper tr')
    return await rows.count()
  }
}
