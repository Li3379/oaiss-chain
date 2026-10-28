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
  await page.waitForTimeout(900)
}

async function selectByText(
  page: import('@playwright/test').Page,
  selectIndex: number,
  optionText: RegExp,
) {
  await page.locator('.search-form .el-select').nth(selectIndex).click()
  const option = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: optionText }).first()
  await option.click()
}

test('probe admin users combined filters and carbon keyword search', async ({ page }) => {
  test.setTimeout(60_000)
  ensureOutDir()
  await loginViaApi(page, 'admin', 'admin123')

  await page.goto(`${BASE_URL}/admin/system/users`)
  await settle(page)

  const usersBefore = await page.locator('.el-table__body tbody tr').count()
  await selectByText(page, 0, /审核员|Reviewer/i)
  await selectByText(page, 1, /禁用|Disabled/i)

  await page.getByRole('button', { name: /查询|search/i }).first().click()
  await settle(page)
  const usersAfterCombinedFilter = await page.locator('.el-table__body tbody tr').count()

  await page.goto(`${BASE_URL}/admin/system/carbon`)
  await settle(page)
  const carbonBefore = await page.locator('.el-table__body tbody tr').count()
  const impossible = `NO_MATCH_${Date.now()}`
  await page.locator('.search-form input:not([readonly])').first().fill(impossible)
  await page.getByRole('button', { name: /查询|search/i }).first().click()
  await settle(page)
  const carbonAfterKeyword = await page.locator('.el-table__body tbody tr').count()

  await page.screenshot({
    path: path.join(OUT_DIR, 'admin-filter-probe-2026-05-24.png'),
    fullPage: true,
  })

  const probe = {
    usersBefore,
    usersAfterCombinedFilter,
    carbonBefore,
    impossible,
    carbonAfterKeyword,
  }
  writeJson('admin-filter-probe-2026-05-24.json', probe)

  expect(usersBefore).toBeGreaterThan(0)
  expect(usersAfterCombinedFilter).toBeLessThanOrEqual(usersBefore)
  expect(carbonBefore).toBeGreaterThan(0)
  expect(carbonAfterKeyword).toBe(0)
})
