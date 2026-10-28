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

test('probe admin users pagination follows en-US locale', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'admin', 'admin123')

  await page.addInitScript(() => {
    localStorage.setItem('locale', 'en-US')
  })

  await page.goto(`${BASE_URL}/admin/system/users`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const bodyText = ((await page.locator('body').textContent()) || '').replace(/\s+/g, ' ').trim()
  const hasEnglishPage = /User Management|Total .* items|Go to|page/i.test(bodyText)
  const hasChinesePagination = /前往|页|条/.test(bodyText)

  writeJson('admin-users-i18n-probe-2026-05-26.json', {
    hasEnglishPage,
    hasChinesePagination,
    bodyText,
  })

  await page.screenshot({
    path: path.join(OUT_DIR, 'admin-users-i18n-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(hasEnglishPage).toBe(true)
  expect(hasChinesePagination).toBe(false)
})
