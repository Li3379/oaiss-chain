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

test('probe credit score localized level, history tag mapping, and ranking table', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')
  await page.goto(`${BASE_URL}/enterprise/credit/score`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1000)

  const levelText = ((await page.locator('.score-level').textContent()) || '').trim()
  const levelClass = (await page.locator('.score-level').getAttribute('class')) || ''
  const historyTags = await page.locator('.el-table .el-tag').evaluateAll((nodes) =>
    nodes.map((node) => ({
      text: (node.textContent || '').trim(),
      className: node.getAttribute('class') || '',
    })),
  )
  const rankingTable = page.getByTestId('credit-ranking-table')
  await expect(rankingTable).toBeVisible()
  const rankingRows = await rankingTable.locator('tbody tr').evaluateAll((rows) =>
    rows.slice(0, 5).map((row) => row.textContent?.trim() || ''),
  )

  await page.screenshot({
    path: path.join(OUT_DIR, 'credit-score-page-fixed-2026-05-24.png'),
    fullPage: true,
  })

  const probe = {
    levelText,
    levelClass,
    historyTags,
    rankingRows,
  }

  writeJson('credit-score-probe-fixed-2026-05-24.json', probe)
})
