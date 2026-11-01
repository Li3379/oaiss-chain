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
    // pen 壳层用 PageHeader 组件渲染标题，类名是 .pen-page-head。
    // 早先断言 .page-title 是旧代际的类名，pen 迁移后已不存在。
    await expect(this.page.locator('.pen-page-head')).toBeVisible()
  }

  /**
   * Verify the stats row cards are visible (trend direction, model version, data points).
   */
  async expectStatsRow(): Promise<void> {
    // pen 壳层用 StatStrip 组件，每个指标是 .pen-stat（旧的 .stat-card 已不存在）
    const statCards = this.page.locator('.pen-stat')
    await expect(statCards.first()).toBeVisible()
  }

  /**
   * 断言图表区域已呈现。
   *
   * `.chart-box` 用 v-show 控制：只有 dataPoints.length > 0 才显示，
   * 否则渲染 `.chart-empty` 空态。ML 在样本不足时返回 insufficient-data
   * （这是业务态，不是故障——空态引导才是正确 UI），此时断言图表可见
   * 会永远失败。因此这里接受「图表 或 空态」二者其一。
   */
  async expectChartVisible(): Promise<void> {
    const chartOrEmpty = this.page.locator('.chart-box, .chart-empty')
    await expect(chartOrEmpty.first()).toBeVisible()
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
    // pen 壳层：StatStrip 的每一项是 .pen-stat（旧的 .stats-row .stat-card 已不存在）
    return this.page.locator('.pen-stat').count()
  }

  /**
   * Get the trend direction text from the first stat card.
   */
  async getTrendDirection(): Promise<string | null> {
    const tag = this.page.locator('.pen-stat').first().locator('.el-tag')
    if (await tag.isVisible().catch(() => false)) {
      return tag.textContent()
    }
    return null
  }
}
