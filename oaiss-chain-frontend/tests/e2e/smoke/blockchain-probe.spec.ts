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

test('probe blockchain browser status and transaction lookup', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')
  await page.goto(`${BASE_URL}/enterprise/blockchain/browser`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1000)

  const statusTag = page.getByTestId('blockchain-status-tag')
  await expect(statusTag).toBeVisible()
  const statusText = ((await statusTag.textContent()) || '').trim()
  const statusCards = await page.locator('.status-metric').evaluateAll((nodes) =>
    nodes.map((node) => node.textContent?.trim() || ''),
  )

  await page.getByTestId('blockchain-tx-query-input').fill('tx_probe_manual_001')
  await page.getByTestId('blockchain-tx-query-submit').click()
  await expect(page.getByTestId('blockchain-tx-query-result')).toBeVisible()
  const queryResultText = ((await page.getByTestId('blockchain-tx-query-result').textContent()) || '').trim()

  await page.getByRole('tab').filter({ hasText: /Transactions|交易/ }).click().catch(() => {})
  await page.waitForTimeout(500)

  const txRows = await page.locator('table tbody tr').evaluateAll((rows) =>
    rows.slice(0, 3).map((row) => row.textContent?.trim() || ''),
  )
  const statusTexts = await page.locator('.el-table .el-tag').allTextContents()

  await page.screenshot({
    path: path.join(OUT_DIR, 'blockchain-browser-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('blockchain-probe-fixed-2026-05-24.json', {
    statusText,
    statusCards,
    queryResultText,
    txRows,
    statusTexts,
  })
})
