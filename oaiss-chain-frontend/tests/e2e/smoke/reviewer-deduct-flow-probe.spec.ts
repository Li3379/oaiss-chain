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

test('probe reviewer deduct flow emits feedback and request behavior', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'reviewer001', 'admin123')

  const requests: string[] = []
  page.on('request', (request) => {
    const url = request.url()
    if (url.includes('/api/v1/credit/deduct')) {
      requests.push(`${request.method()} ${url}`)
    }
  })

  await page.goto(`${BASE_URL}/auditor/project/review`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const deductBtn = page.locator('button').filter({ hasText: /扣分|Deduct/i }).first()
  await expect(deductBtn).toBeVisible()
  await deductBtn.click()

  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog).toBeVisible()

  const confirmBtn = dialog.locator('button').filter({ hasText: /确认扣分|Confirm Deduction/i }).first()
  await confirmBtn.click()
  await page.waitForTimeout(500)

  const requestsAfterEmptySubmit = requests.length
  const warningVisible = await page.locator('.el-message--warning').count().then((n) => n > 0).catch(() => false)

  const textarea = dialog.locator('textarea').first()
  await textarea.fill(`qa deduct probe ${Date.now()}`)
  await confirmBtn.click()
  await page.waitForTimeout(1200)

  const requestsAfterFilledSubmit = requests.length
  const hasDeductRequest = requestsAfterFilledSubmit > requestsAfterEmptySubmit
  const hasToastFeedback = await page.locator('.el-message').count().then((n) => n > 0).catch(() => false)

  writeJson('reviewer-deduct-flow-probe-2026-05-26.json', {
    requests,
    requestsAfterEmptySubmit,
    requestsAfterFilledSubmit,
    warningVisible,
    hasDeductRequest,
    hasToastFeedback,
  })

  await page.screenshot({
    path: path.join(OUT_DIR, 'reviewer-deduct-flow-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(warningVisible).toBe(true)
  expect(hasDeductRequest).toBe(true)
  expect(hasToastFeedback).toBe(true)
})
