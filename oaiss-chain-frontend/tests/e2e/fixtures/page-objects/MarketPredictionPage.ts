import { type Page, expect } from '@playwright/test'

/**
 * Page object for MarketPrediction.vue
 * Route: /enterprise/market-prediction
 *
 * Page structure:
 * - Header with page title + prediction type selector (el-select)
 * - Stats row: trend direction, model version, data points count
 * - Horizon selector: el-button group (7/30/90/180 days)
 * - Single ECharts chart in div.chart-box
 */
export class MarketPredictionPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/market-prediction')
    await this.page.waitForLoadState('networkidle')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/market-prediction/)
    // Wait for the page title (rendered via i18n key enterprise.marketPrediction.title)
    await expect(this.page.locator('.page-title')).toBeVisible()
  }

  /**
   * Verify the stats row cards are visible (trend direction, model version, data points).
   */
  async expectStatsRow(): Promise<void> {
    const statCards = this.page.locator('.stat-card')
    await expect(statCards.first()).toBeVisible()
  }

  /**
   * Verify the ECharts chart container is rendered.
   * MarketPrediction uses a single chart in div.chart-box.
   */
  async expectChartVisible(): Promise<void> {
    const chartBox = this.page.locator('.chart-box')
    await expect(chartBox).toBeVisible()
  }

  /**
   * Verify the horizon day selector buttons are present.
   * Options: 7, 30, 90, 180 days.
   */
  async expectHorizonSelector(): Promise<void> {
    const horizonRow = this.page.locator('.horizon-row')
    await expect(horizonRow).toBeVisible()
  }

  /**
   * Click a specific horizon days button (7, 30, 90, or 180).
   */
  async selectHorizon(days: number): Promise<void> {
    const button = this.page.locator('.horizon-row').getByRole('button', { exact: false, name: String(days) })
    await button.click()
  }

  /**
   * Select a prediction type from the el-select dropdown.
   * Values: 'trend', 'price', 'supply-demand'
   */
  async selectPredictionType(type: 'trend' | 'price' | 'supply-demand'): Promise<void> {
    const select = this.page.locator('.header-row').getByRole('combobox')
    await select.click()
    // Wait for dropdown options to appear, then click the matching option
    const option = this.page.locator('.el-select-dropdown__item').nth(
      type === 'trend' ? 0 : type === 'price' ? 1 : 2,
    )
    await option.click()
  }

  /**
   * Click the refresh/reload action by re-selecting current horizon.
   * (MarketPrediction auto-fetches on type/horizon change via watchers.)
   */
  async triggerRefresh(): Promise<void> {
    await this.selectHorizon(30)
  }

  /**
   * Get the number of stat cards visible in the stats row.
   */
  async getStatCardCount(): Promise<number> {
    return this.page.locator('.stats-row .stat-card').count()
  }

  /**
   * Get the trend direction text from the first stat card.
   */
  async getTrendDirection(): Promise<string | null> {
    const tag = this.page.locator('.stats-row .stat-card').first().locator('.el-tag')
    if (await tag.isVisible().catch(() => false)) {
      return tag.textContent()
    }
    return null
  }
}
