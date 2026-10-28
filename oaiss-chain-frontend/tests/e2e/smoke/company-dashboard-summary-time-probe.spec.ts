import { test, expect, type Page, type Request } from '@playwright/test'
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

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(1000)
}

async function readOverviewValues(page: Page): Promise<string[]> {
  const values = page.locator('.overview-card .metric-value')
  await expect(values).toHaveCount(3)
  return values.allTextContents()
}

async function clickTimeDimension(page: Page, matcher: RegExp) {
  await page.locator('.search-right .el-radio-button__inner').filter({ hasText: matcher }).first().click()
}

async function captureDashboardRequests(page: Page, action: () => Promise<void>) {
  const urls: string[] = []
  const handler = (request: Request) => {
    const url = request.url()
    if (
      url.includes('/api/v1/trade/my-trades') ||
      url.includes('/api/v1/carbon/my-reports') ||
      url.includes('/api/v1/enterprise/dashboard')
    ) {
      urls.push(url)
    }
  }
  page.on('request', handler)
  try {
    await action()
    await page.waitForTimeout(1200)
  } finally {
    page.off('request', handler)
  }
  return urls
}

test('probe company dashboard summary source and time dimension effect', async ({ page }) => {
  test.setTimeout(60_000)
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')

  await page.goto(`${BASE_URL}/enterprise/company/dashboard`)
  // Wait for loading to finish and overview cards to render (race condition fix)
  await page.locator('.overview-card').first().waitFor({ state: 'visible', timeout: 15000 }).catch(() => {})
  await settle(page)

  const beforeValues = (await readOverviewValues(page)).map((item) => item.trim())
  const dayRequestUrls = await captureDashboardRequests(page, async () => {
    await clickTimeDimension(page, /日|day/i)
    await settle(page)
  })
  const dayValues = (await readOverviewValues(page)).map((item) => item.trim())

  const yearRequestUrls = await captureDashboardRequests(page, async () => {
    await clickTimeDimension(page, /年|year/i)
    await settle(page)
  })
  const yearValues = (await readOverviewValues(page)).map((item) => item.trim())

  await page.screenshot({
    path: path.join(OUT_DIR, 'company-dashboard-summary-time-2026-05-24.png'),
    fullPage: true,
  })

  const probe = {
    beforeValues,
    dayValues,
    yearValues,
    dayRequestUrls,
    yearRequestUrls,
  }
  writeJson('company-dashboard-summary-time-probe-2026-05-24.json', probe)

  expect(beforeValues.length).toBeGreaterThanOrEqual(3)
  expect(beforeValues.some((item) => item !== '0')).toBeTruthy()
  expect(dayValues).toEqual(beforeValues)
  expect(yearValues).toEqual(beforeValues)
  expect(dayRequestUrls).toEqual([])
  expect(yearRequestUrls).toEqual([])
})
