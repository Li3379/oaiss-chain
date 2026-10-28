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

test('probe reviewer audit dialog no longer emits radio compatibility warning', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'reviewer001', 'admin123')

  const consoleMessages: string[] = []
  page.on('console', (msg) => {
    consoleMessages.push(msg.text())
  })

  await page.goto(`${BASE_URL}/auditor/audit/list`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const reviewBtn = page.locator('.el-table__body .el-button').first()
  await expect(reviewBtn).toBeVisible()
  await reviewBtn.click()
  await page.waitForTimeout(800)

  const hasRadioDeprecation = consoleMessages.some((message) =>
    message.includes('[el-radio] [API] label act as value'),
  )

  writeJson('reviewer-audit-compat-probe-2026-05-26.json', {
    hasRadioDeprecation,
    consoleMessages,
  })

  await page.screenshot({
    path: path.join(OUT_DIR, 'reviewer-audit-compat-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(hasRadioDeprecation).toBe(false)
})
