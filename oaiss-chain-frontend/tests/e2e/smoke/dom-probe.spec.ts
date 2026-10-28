import { test } from '@playwright/test'
import { loginViaApi } from '../fixtures/auth'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5174'

test('probe dashboard DOM classes', async ({ page }) => {
  test.setTimeout(30_000)
  await loginViaApi(page, 'enterprise001', 'admin123')
  await page.goto(`${BASE_URL}/enterprise/company/dashboard`)
  await page.waitForTimeout(3000)

  const result = await page.evaluate(() => {
    const grid = document.querySelector('.overview-grid')
    const cards = document.querySelectorAll('.overview-card')
    const metrics = document.querySelectorAll('.metric-value')
    const allWithOverview = document.querySelectorAll('[class*="overview"]')
    const allWithMetric = document.querySelectorAll('[class*="metric"]')
    
    // Get all elements in the main content area and their class lists
    const mainEl = document.querySelector('.main-content, .app-main, main, [class*="main"]')
    const mainChildren = mainEl ? Array.from(mainEl.querySelectorAll('*')).slice(0, 50).map(el => ({
      tag: el.tagName,
      class: el.className,
      text: (el.textContent || '').trim().substring(0, 60)
    })) : []

    return {
      overviewCardsCount: cards.length,
      metricValuesCount: metrics.length,
      overviewGridExists: !!grid,
      allWithOverviewCount: allWithOverview.length,
      allWithMetricCount: allWithMetric.length,
      overviewClasses: Array.from(allWithOverview).map(e => e.className),
      metricClasses: Array.from(allWithMetric).map(e => e.className),
      mainChildren
    }
  })
  
  console.log(JSON.stringify(result, null, 2))
})
