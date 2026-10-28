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

test('probe admin users default status filter is neutral', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'admin', 'admin123')

  const requests: string[] = []
  page.on('request', (request) => {
    const url = request.url()
    if (url.includes('/api/v1/admin/users')) {
      requests.push(`${request.method()} ${url}`)
    }
  })

  await page.goto(`${BASE_URL}/admin/system/users`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const selectTexts = (await page.locator('.search-form .el-select').allTextContents().catch(() => []))
    .map((text) => text.replace(/\s+/g, ' ').trim())
  const statusFilterText = selectTexts[1] || ''
  const initialUsersRequest = requests[0] || ''
  const requestHasStatusParam = /[?&]status=/.test(initialUsersRequest)

  writeJson('admin-users-default-filter-probe-2026-05-26.json', {
    selectTexts,
    statusFilterText,
    initialUsersRequest,
    requestHasStatusParam,
  })

  await page.screenshot({
    path: path.join(OUT_DIR, 'admin-users-default-filter-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(requestHasStatusParam).toBe(false)
  expect(statusFilterText).not.toMatch(/Enabled|Disabled|鍚敤|绂佺敤/i)
})
