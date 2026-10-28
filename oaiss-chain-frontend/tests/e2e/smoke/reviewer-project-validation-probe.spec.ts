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

test('probe reviewer project verification blocks empty verified reduction before request', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'reviewer001', 'admin123')

  const requests: string[] = []
  page.on('request', (request) => {
    const url = request.url()
    if (url.includes('/api/v1/carbon-neutral/verify')) {
      requests.push(`${request.method()} ${url}`)
    }
  })

  await page.goto(`${BASE_URL}/auditor/project/review`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const verifyBtn = page.locator('.el-table__body .el-button').first()
  await expect(verifyBtn).toBeVisible()
  await verifyBtn.click()

  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog).toBeVisible()

  const verifiedReductionInput = dialog.locator('input').first()
  await verifiedReductionInput.fill('')

  const submitBtn = dialog.locator('.el-dialog__footer .el-button').last()
  await submitBtn.click()
  await page.waitForTimeout(800)

  const requestCount = requests.length
  const bodyText = ((await dialog.textContent()) || '').trim()
  const hasWarningToast = await page.locator('.el-message--warning').count().then((n) => n > 0).catch(() => false)
  const hasInlineValidation = bodyText.includes('> 0') || /greater than 0|Verified Reduction/.test(bodyText)

  writeJson('reviewer-project-validation-probe-2026-05-26.json', {
    requestCount,
    requests,
    hasWarningToast,
    hasInlineValidation,
    bodyText,
  })

  await page.screenshot({
    path: path.join(OUT_DIR, 'reviewer-project-validation-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(requestCount).toBe(0)
  expect(hasInlineValidation || hasWarningToast).toBe(true)
})
