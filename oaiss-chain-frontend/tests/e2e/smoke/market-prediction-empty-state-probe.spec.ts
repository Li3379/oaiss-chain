import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { loginViaApi } from '../fixtures/auth'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const OUT_DIR = path.join(process.cwd(), 'test-results', 'ad-hoc-probes')

function ensureOutDir() {
  fs.mkdirSync(OUT_DIR, { recursive: true })
}

function writeJson(name: string, data: unknown) {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(data, null, 2), 'utf8')
}

const fallbackPayload = {
  code: 200,
  message: 'success',
  data: {
    forecastDates: null,
    forecastPrices: null,
    lowerBound: null,
    upperBound: null,
    trend: 'unknown',
    modelVersion: 'fallback',
  },
}

test('probe market prediction empty state for fallback responses', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')

  await page.route('**/api/v1/ai/market/**', async (route) => {
    await route.fulfill({ json: fallbackPayload })
  })

  await page.goto(`${BASE_URL}/enterprise/market-prediction`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1000)

  const trendText = ((await page.locator('.stats-row .stat-card').nth(0).textContent()) || '').trim()
  const modelVersionText = ((await page.locator('.stats-row .stat-card').nth(1).textContent()) || '').trim()
  const dataPointsText = ((await page.locator('.stats-row .stat-card').nth(2).textContent()) || '').trim()
  const emptyVisible = await page.getByText(/暂无预测数据|No prediction data available/i).isVisible().catch(() => false)
  const canvasCount = await page.locator('canvas').count()

  expect(emptyVisible).toBe(true)
  expect(canvasCount).toBe(0)

  await page.screenshot({
    path: path.join(OUT_DIR, 'market-prediction-empty-state-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('market-prediction-empty-state-probe-fixed-2026-05-24.json', {
    trendText,
    modelVersionText,
    dataPointsText,
    emptyVisible,
    canvasCount,
  })
})
