import { test, type Page } from '@playwright/test'
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

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 6000 }).catch(() => {})
  await page.waitForTimeout(700)
}

test('probe reviewer project review row/action consistency', async ({ page }) => {
  test.setTimeout(70_000)
  ensureOutDir()
  await loginViaApi(page, 'reviewer001', 'admin123')

  await page.goto(`${BASE_URL}/auditor/project/review`)
  await settle(page)

  const rows = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
  const statusCells = await page.locator('.el-table__body tbody tr td:nth-child(4)').allTextContents().catch(() => [])
  const verificationStatusCells = await page.locator('.el-table__body tbody tr td:nth-child(5)').allTextContents().catch(() => [])
  const operationCells = await page.locator('.el-table__body tbody tr td:last-child').allTextContents().catch(() => [])
  const rowActionTexts = await page.locator('.el-table__body tbody tr').evaluateAll((tableRows) =>
    tableRows.map((row) =>
      Array.from(row.querySelectorAll('button'))
        .map((button) => (button.textContent || '').trim())
        .filter(Boolean),
    ),
  ).catch(() => [] as string[][])

  const allActionTexts = rowActionTexts.flat()
  const hasReviewButton = allActionTexts.some((text) => /review|审核|審核/i.test(text))
  const hasVerifyButton = allActionTexts.some((text) => /verify|核证|核驗|认证|認證/i.test(text))
  const hasDeductButton = allActionTexts.some((text) => /credit|扣分/i.test(text))
  const hasPendingVerificationRow = verificationStatusCells.some((text) => /核证中|pending/i.test(text))
  const hasDualActionRow = rowActionTexts.some((texts) => texts.length >= 2)

  await page.screenshot({ path: path.join(OUT_DIR, 'role-lifecycle-reviewer-project-2026-05-24.png'), fullPage: true })
  writeJson('role-lifecycle-reviewer-project-2026-05-24.json', {
    url: page.url(),
    rows,
    statusCells,
    verificationStatusCells,
    operationCells,
    rowActionTexts,
    hasReviewButton,
    hasVerifyButton,
    hasDeductButton,
    hasPendingVerificationRow,
    hasDualActionRow,
  })
})

test('probe admin certificate tabs identifier/domain consistency', async ({ page }) => {
  test.setTimeout(80_000)
  ensureOutDir()
  await loginViaApi(page, 'admin', 'admin123')

  await page.goto(`${BASE_URL}/admin/certificates`)
  await settle(page)

  const admissionRows = await page.locator('.el-tab-pane.is-active .el-table__body tbody tr').count().catch(() => 0)
  const admissionEnterpriseIds = await page.locator('.el-tab-pane.is-active .el-table__body tbody tr td:nth-child(2)').allTextContents().catch(() => [])

  const qualificationTab = page.locator('.el-tabs__item').filter({ hasText: /Qualification|资质|資格/i }).first()
  if (await qualificationTab.count()) {
    await qualificationTab.click().catch(() => {})
    await settle(page)
  }

  const qualificationRows = await page.locator('.el-tab-pane.is-active .el-table__body tbody tr').count().catch(() => 0)
  const qualificationReviewerIds = await page.locator('.el-tab-pane.is-active .el-table__body tbody tr td:nth-child(2)').allTextContents().catch(() => [])
  const qualificationTypes = await page.locator('.el-tab-pane.is-active .el-table__body tbody tr td:nth-child(4)').allTextContents().catch(() => [])

  await page.screenshot({ path: path.join(OUT_DIR, 'role-lifecycle-admin-certificates-2026-05-24.png'), fullPage: true })
  writeJson('role-lifecycle-admin-certificates-2026-05-24.json', {
    url: page.url(),
    admissionRows,
    admissionEnterpriseIds,
    qualificationRows,
    qualificationReviewerIds,
    qualificationTypes,
  })
})
