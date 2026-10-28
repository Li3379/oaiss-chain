import { type Page, expect } from '@playwright/test'

/**
 * Page object for EnterpriseInference.vue
 * Route: /enterprise/enterprise-inference
 *
 * Page structure:
 * - PageContainer wrapper with title + description
 * - Card with header ("Inference Results" + refresh button)
 * - Stat cards: compliance status, confidence score, anomaly score, anomaly detection
 * - Risk factors section (el-tag list)
 * - Model version footer
 * - No ECharts — uses el-progress (dashboard) and el-tag for display
 */
export class EnterpriseInferencePage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/enterprise-inference')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/enterprise-inference/)
    // PageContainer renders the title
    await expect(this.page.locator('.inference-page')).toBeVisible()
  }

  /**
   * Verify the compliance status tag is visible.
   * Shows one of: compliant, warning, non-compliant (via el-tag with type).
   */
  async expectComplianceStatus(): Promise<void> {
    const statCards = this.page.locator('.stat-card')
    await expect(statCards.first()).toBeVisible()
  }

  /**
   * Verify the stat row (4 cards) is rendered.
   * Cards: compliance status, confidence score, anomaly score, anomaly detection.
   */
  async expectStatCards(): Promise<void> {
    const statCards = this.page.locator('.stat-row .stat-card')
    const count = await statCards.count()
    expect(count).toBeGreaterThanOrEqual(1)
  }

  /**
   * Check if risk factors section is present.
   * May show el-tag list (if risk factors exist) or el-alert (if no risk factors).
   */
  async expectRiskSection(): Promise<void> {
    // Either risk factors tags or a "no risk factors" alert should be visible
    const riskTags = this.page.locator('.risk-factors')
    const noRiskAlert = this.page.locator('.el-alert')
    const hasTags = await riskTags.isVisible().catch(() => false)
    const hasAlert = await noRiskAlert.isVisible().catch(() => false)
    expect(hasTags || hasAlert).toBeTruthy()
  }

  /**
   * Click the refresh button in the card header.
   */
  async clickRefresh(): Promise<void> {
    const refreshBtn = this.page.locator('.card-header-row').getByRole('button')
    await refreshBtn.click()
  }

  /**
   * Get the compliance status tag text.
   */
  async getComplianceStatus(): Promise<string | null> {
    const tag = this.page.locator('.stat-row .stat-card').first().locator('.el-tag')
    if (await tag.isVisible().catch(() => false)) {
      return tag.textContent()
    }
    return null
  }

  /**
   * Check if the empty state is shown (no inference data).
   */
  async isEmptyState(): Promise<boolean> {
    const empty = this.page.locator('.el-empty')
    return empty.isVisible().catch(() => false)
  }
}
