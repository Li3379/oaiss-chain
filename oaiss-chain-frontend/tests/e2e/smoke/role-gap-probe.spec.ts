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

async function loginAs(page: Page, role: 'admin' | 'reviewer' | 'thirdParty') {
  if (role === 'admin') {
    await loginViaApi(page, 'admin', 'admin123')
    return
  }
  if (role === 'reviewer') {
    await loginViaApi(page, 'reviewer001', 'admin123')
    return
  }
  await loginViaApi(page, 'thirdparty001', 'admin123')
}

test('probe admin verify/detail and config backend wiring', async ({ page }) => {
  test.setTimeout(90_000)
  ensureOutDir()
  await loginAs(page, 'admin')

  const configRequestLog: string[] = []
  page.on('request', (req) => {
    const url = req.url()
    if (url.includes('/api/v1/admin/config')) {
      configRequestLog.push(url)
    }
  })

  await page.goto(`${BASE_URL}/admin/verify/list`)
  await settle(page)

  const verifyUrl = page.url()
  const verifyRows = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
  const verifyHeaderTexts = await page.locator('.el-table__header thead th').allTextContents().catch(() => [])

  let detailOpened = false
  let detailLabels: string[] = []
  let detailBlankValueCount = 0
  let detailTonOnlyFields = 0
  let rowStatusText = ''
  if (verifyRows > 0) {
    rowStatusText = ((await page.locator('.el-table__body tbody tr:first-child .el-tag').first().textContent().catch(() => '')) || '').trim()
    const viewBtn = page.locator('.el-table__body tbody tr:first-child .el-button').filter({ hasText: /查看|view/i }).first()
    if (await viewBtn.count()) {
      await viewBtn.click().catch(() => {})
      await settle(page)
      detailOpened = await page.locator('.el-dialog:visible').count().then(n => n > 0).catch(() => false)
      detailLabels = await page.locator('.el-dialog:visible .el-descriptions__label').allTextContents().catch(() => [])
      const values = await page.locator('.el-dialog:visible .el-descriptions__content').allTextContents().catch(() => [])
      const normalized = values.map(v => (v || '').replace(/\s+/g, ' ').trim())
      detailBlankValueCount = normalized.filter(v => v === '' || v === '-' || v === 'null').length
      detailTonOnlyFields = normalized.filter(v => v === '吨' || v === 'tCO2e' || v === 'kgCO2e').length
    }
  }
  await page.screenshot({ path: path.join(OUT_DIR, 'role-gap-admin-verify-2026-05-24.png'), fullPage: true })

  await page.goto(`${BASE_URL}/admin/system/config`)
  await settle(page)
  const configUrl = page.url()
  const configRowsBefore = await page.locator('.el-table__body tbody tr').count().catch(() => 0)

  const configProbe = {
    verifyUrl,
    verifyRows,
    rowStatusText,
    verifyHeaderTexts,
    detailOpened,
    detailLabels,
    detailBlankValueCount,
    detailTonOnlyFields,
    configUrl,
    configRowsBefore,
    configRequestCount: configRequestLog.length,
    configRequestLog,
  }
  writeJson('role-gap-admin-probe-2026-05-24.json', configProbe)
  await page.screenshot({ path: path.join(OUT_DIR, 'role-gap-admin-config-2026-05-24.png'), fullPage: true })
})

test('probe reviewer pending/all/history rendering gaps', async ({ page }) => {
  test.setTimeout(90_000)
  ensureOutDir()
  await loginAs(page, 'reviewer')

  await page.goto(`${BASE_URL}/auditor/audit/list`)
  await settle(page)

  const pendingRows = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
  const pendingEnterpriseCells = await page.locator('.el-table__body tbody tr td:nth-child(2)').allTextContents().catch(() => [])
  const pendingStatusCells = await page.locator('.el-table__body tbody tr td:nth-child(5)').allTextContents().catch(() => [])
  const pendingOperationCells = await page.locator('.el-table__body tbody tr td:last-child').allTextContents().catch(() => [])

  const statsText = ((await page.locator('.section-card').nth(1).textContent().catch(() => '')) || '').replace(/\s+/g, ' ').trim()
  const reviewerNameLine = ((await page.locator('.section-card').first().textContent().catch(() => '')) || '').replace(/\s+/g, ' ').trim()

  const allTab = page.locator('.el-tabs__item').filter({ hasText: /全部报告|all/i }).first()
  if (await allTab.count()) {
    await allTab.click().catch(() => {})
    await settle(page)
  }
  const allRows = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
  const allStatusCells = await page.locator('.el-table__body tbody tr td:nth-child(5)').allTextContents().catch(() => [])
  const allOperationCells = await page.locator('.el-table__body tbody tr td:last-child').allTextContents().catch(() => [])

  await page.screenshot({ path: path.join(OUT_DIR, 'role-gap-reviewer-audit-list-2026-05-24.png'), fullPage: true })

  await page.goto(`${BASE_URL}/auditor/review/history`)
  await settle(page)
  const historyRows = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
  const historyEmptyVisible = await page.locator('.el-empty, .el-table__empty-text').count().then(n => n > 0).catch(() => false)
  const historyRowTexts = await page.locator('.el-table__body tbody tr').allTextContents().catch(() => [])
  await page.screenshot({ path: path.join(OUT_DIR, 'role-gap-reviewer-history-2026-05-24.png'), fullPage: true })

  const reviewerProbe = {
    pendingRows,
    pendingEnterpriseCells,
    pendingStatusCells,
    pendingOperationCells,
    statsText,
    reviewerNameLine,
    allRows,
    allStatusCells,
    allOperationCells,
    historyRows,
    historyEmptyVisible,
    historyRowTexts,
  }
  writeJson('role-gap-reviewer-probe-2026-05-24.json', reviewerProbe)
})

test('probe third-party monitor filter/info entry gaps', async ({ page }) => {
  test.setTimeout(70_000)
  ensureOutDir()
  await loginAs(page, 'thirdParty')

  await page.goto(`${BASE_URL}/third-party/monitor`)
  await settle(page)

  const monitorRows = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
  const filterInputCount = await page.locator('.search-form input, .search-form .el-select').count().catch(() => 0)
  const hasOrgInfoLabel = await page.locator('body').textContent().then(t => /机构信息|组织信息|联系方式|contact|org/i.test(t || '')).catch(() => false)
  const actionColumnVisible = await page.locator('.el-table__header th').allTextContents().then((th) => th.some((x) => /操作|operation/i.test(x))).catch(() => false)
  await page.screenshot({ path: path.join(OUT_DIR, 'role-gap-third-party-monitor-2026-05-24.png'), fullPage: true })

  const thirdPartyProbe = {
    monitorRows,
    filterInputCount,
    hasOrgInfoLabel,
    actionColumnVisible,
    url: page.url(),
  }
  writeJson('role-gap-third-party-probe-2026-05-24.json', thirdPartyProbe)
})

