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

test('probe carbon coin account summary uses real backend fields', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')
  await page.goto(`${BASE_URL}/enterprise/carbon-coin/account`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1000)

  const summaryLabels = await page.locator('.account-info .info-label').allTextContents()
  const statusCard = page.locator('[data-testid="carbon-coin-status-card"]')
  await expect(statusCard).toBeVisible()

  const statusText = ((await statusCard.textContent()) || '').trim()
  const hasFrozenAmountLabel = summaryLabels.some((label) => label.includes('冻结金额') || label.includes('Frozen Amount'))

  expect(hasFrozenAmountLabel).toBe(false)
  expect(statusText.length).toBeGreaterThan(0)

  await page.screenshot({
    path: path.join(OUT_DIR, 'carbon-coin-account-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('carbon-coin-account-probe-fixed-2026-05-24.json', {
    summaryLabels,
    statusText,
  })
})
