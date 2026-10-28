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

test('probe admin system config backend wiring and localized semantics', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'admin', 'admin123')

  const requests: string[] = []
  page.on('request', (request) => {
    const url = request.url()
    if (url.includes('/api/v1/admin/config')) {
      requests.push(`${request.method()} ${url}`)
    }
  })

  await page.goto(`${BASE_URL}/admin/system/config`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const bodyText = ((await page.locator('body').textContent()) || '').replace(/\s+/g, ' ').trim()
  const headerTexts = (await page.locator('.el-table th').allTextContents().catch(() => []))
    .map((text) => text.replace(/\s+/g, ' ').trim())
    .filter(Boolean)
  const rowTexts = (await page.locator('.el-table__body tbody tr').allTextContents().catch(() => []))
    .map((text) => text.replace(/\s+/g, ' ').trim())
    .filter(Boolean)

  const hasConfigRequest = requests.some((entry) => entry.includes('/api/v1/admin/config'))
  const hasRefreshRawKey = bodyText.includes('common.refresh')
  const hasEnglishDescriptionLeak = /System display name|Blockchain integration switch|backend/i.test(bodyText)

  writeJson('admin-config-state-probe-2026-05-26.json', {
    requests,
    hasConfigRequest,
    hasRefreshRawKey,
    hasEnglishDescriptionLeak,
    headerTexts,
    rowTexts,
    bodyText,
  })

  await page.screenshot({
    path: path.join(OUT_DIR, 'admin-config-state-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(hasConfigRequest).toBe(true)
  expect(hasRefreshRawKey).toBe(false)
  expect(hasEnglishDescriptionLeak).toBe(false)
  expect(headerTexts.length).toBeGreaterThan(0)
  expect(rowTexts.length).toBeGreaterThan(0)
})
