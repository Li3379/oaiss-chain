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

test('probe admin verify detail dialog renders scope emission values', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'admin', 'admin123')

  await page.goto(`${BASE_URL}/admin/verify/list`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const firstRow = page.locator('.el-table__body tbody tr').first()
  await expect(firstRow).toBeVisible()
  const viewBtn = firstRow.locator('td:last-child .el-button, td:last-child button').first()
  await expect(viewBtn).toBeVisible()
  await viewBtn.click()

  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog).toBeVisible()

  const detailTexts = (await dialog.locator('.el-descriptions__content').allTextContents().catch(() => []))
    .map((text) => text.replace(/\s+/g, ' ').trim())
  const emissionFields = detailTexts.slice(4, 8)
  const emissionFieldsWithNumber = emissionFields.filter((text) => /\d/.test(text))
  const unitOnlyFields = emissionFields.filter((text) => !/\d/.test(text))
  const blankLikeFields = detailTexts.filter((text) => text === '' || text === '-' || text === 'null')

  writeJson('admin-verify-detail-probe-2026-05-26.json', {
    detailTexts,
    emissionFields,
    emissionFieldsWithNumber,
    unitOnlyFields,
    blankLikeFields,
  })

  await page.screenshot({
    path: path.join(OUT_DIR, 'admin-verify-detail-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(emissionFields).toHaveLength(4)
  expect(emissionFieldsWithNumber).toHaveLength(4)
  expect(unitOnlyFields).toHaveLength(0)
})
