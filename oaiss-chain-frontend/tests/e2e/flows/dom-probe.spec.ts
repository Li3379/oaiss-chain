import { test } from '@playwright/test'
import { loginViaApi } from '../fixtures/auth'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5174'

test('probe dashboard DOM classes', async ({ page }) => {
  test.setTimeout(60_000)
  await loginViaApi(page, 'enterprise001', 'admin123')
  await page.goto(`${BASE_URL}/enterprise/company/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(5000)

  const result = await page.evaluate(() => {
    const overviewCards = document.querySelectorAll('.overview-card')
    const metricValues = document.querySelectorAll('.metric-value')
    const overviewGrid = document.querySelector('.overview-grid')
    const loadingEl = document.querySelector('.loading-container')

    return {
      overviewCardsCount: overviewCards.length,
      metricValuesCount: metricValues.length,
      overviewGridExists: !!overviewGrid,
      loadingExists: !!loadingEl,
      url: window.location.href,
      mainHTML: document.querySelector('main')?.innerHTML?.substring(0, 800) || 'NO MAIN',
      allText: document.querySelector('main')?.textContent?.substring(0, 300) || '',
    }
  })

  console.log(JSON.stringify(result, null, 2))
})
