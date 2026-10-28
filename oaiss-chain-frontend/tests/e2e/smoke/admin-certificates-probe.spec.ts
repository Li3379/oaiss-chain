import { test } from '@playwright/test'
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

test('probe admin certificates page against live backend data', async ({ page }) => {
  test.setTimeout(20_000)
  ensureOutDir()
  await loginViaApi(page, 'admin', 'admin123')

  const requests: string[] = []
  page.on('request', (req) => {
    const url = req.url()
    if (url.includes('/api/v1/admin/enterprise-admission') || url.includes('/api/v1/admin/reviewer-qualification')) {
      requests.push(`${req.method()} ${url}`)
    }
  })

  await page.goto(`${BASE_URL}/admin/certificates`)
  await page.waitForLoadState('domcontentloaded')
  await page.locator('.el-tabs__nav').waitFor({ state: 'visible', timeout: 5000 })

  const admissionRows = await page.locator('.el-tab-pane.is-active .el-table__body tbody tr').count().catch(() => 0)
  const admissionText = await page.locator('.el-tab-pane.is-active').textContent().catch(() => '')

  const qualificationTab = page.locator('.el-tabs__item').nth(1)
  if ((await qualificationTab.count()) > 0) {
    await qualificationTab.click({ force: true, timeout: 3000 })
    await page.waitForTimeout(800)
  }

  const qualificationRows = await page.locator('.el-tab-pane.is-active .el-table__body tbody tr').count().catch(() => 0)
  const qualificationText = await page.locator('.el-tab-pane.is-active').textContent().catch(() => '')

  await page.screenshot({ path: path.join(OUT_DIR, 'admin-certificates-probe-2026-05-25.png'), fullPage: true })
  writeJson('admin-certificates-probe-2026-05-25.json', {
    url: page.url(),
    requests,
    admissionRows,
    admissionText,
    qualificationRows,
    qualificationText,
  })
})
