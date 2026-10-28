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

async function settle(page: import('@playwright/test').Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(700)
}

test('probe enterprise info fields and quota visibility', async ({ page }) => {
  test.setTimeout(40_000)
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')

  await page.goto(`${BASE_URL}/enterprise/info`)
  await settle(page)

  const text = (await page.locator('body').textContent().catch(() => '')) || ''
  const values = await page.locator('.el-descriptions__cell').allTextContents().catch(() => [])

  await page.screenshot({
    path: path.join(OUT_DIR, 'enterprise-info-page-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('enterprise-info-probe-2026-05-24.json', { values, text })

  expect(text).toContain('绿色能源科技有限公司')
  expect(text).toContain('50000')
  expect(text).toMatch(/10000|11975/)
})
