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
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(300)
}

async function login(page: Page) {
  await loginViaApi(page, 'enterprise001', 'admin123')
}

async function openTab(page: Page, url: string, shotName: string) {
  await page.goto(url)
  await settle(page)
  await page.screenshot({ path: path.join(OUT_DIR, shotName), fullPage: true })
}

test('probe emission data page for display gaps', async ({ page }) => {
  test.setTimeout(40_000)
  ensureOutDir()
  await login(page)

  await openTab(page, `${BASE_URL}/enterprise/emission/data`, 'emission-data-page-2026-05-24.png')
  const emissionSummary = {
    ratingRows: await page.locator('.el-table__body tbody tr').count().catch(() => 0),
    ratingRowTexts: await page.locator('.el-table__body tbody tr').allTextContents().catch(() => []),
  }

  await page.locator('.el-tabs__item').nth(1).click()
  await settle(page)
  const rankingSummary = {
    rankingRows: await page.locator('.el-table__body tbody tr').count().catch(() => 0),
    rankingRowTexts: await page.locator('.el-table__body tbody tr').allTextContents().catch(() => []),
  }
  await page.screenshot({ path: path.join(OUT_DIR, 'emission-data-rankings-tab-2026-05-24.png'), fullPage: true })

  await page.locator('.el-tabs__item').nth(2).click()
  await settle(page)
  const emissionInputs = await page.locator('input, textarea').count()
  const enterpriseInputVisible = await page.locator('input[placeholder*="ID" i]').first().isVisible().catch(() => false)
  const historyTextarea = page.locator('textarea').first()
  if (await historyTextarea.count()) {
    await historyTextarea.fill('[10,20,30,40]')
  }
  await page.getByRole('button', { name: /AI棰勬祴|AI预测|predict/i }).click().catch(() => {})
  await settle(page)
  await page.screenshot({ path: path.join(OUT_DIR, 'emission-data-prediction-attempt-2026-05-24.png'), fullPage: true })
  const emissionProbe = {
    page: 'emission-data',
    ratingTab: emissionSummary,
    rankingTab: rankingSummary,
    emissionInputs,
    enterpriseInputVisible,
    resultText: (await page.locator('body').textContent().catch(() => '')) || '',
  }
  writeJson('emission-data-probe-2026-05-24.json', emissionProbe)
})

test('probe carbon-neutral list and detail actions', async ({ page }) => {
  test.setTimeout(40_000)
  ensureOutDir()
  await login(page)

  await openTab(page, `${BASE_URL}/enterprise/carbon-neutral/projects`, 'carbon-neutral-list-2026-05-24.png')
  const projectRows = page.locator('.el-table__body tbody tr')
  const rowCount = await projectRows.count()
  const firstRowText = rowCount > 0 ? ((await projectRows.first().textContent().catch(() => '')) || '').trim() : ''
  const actionButtons = await page.locator('.el-table__body tbody tr:first-child .el-button, .el-table__body tbody tr:first-child a').allTextContents().catch(() => [])
  let detailStatusText = ''
  let detailActionButtons: string[] = []
  let detailShot = ''
  if (rowCount > 0) {
    await page.locator('.el-table__body tbody tr:first-child a').last().click().catch(() => {})
    await settle(page)
    detailShot = 'carbon-neutral-detail-2026-05-24.png'
    detailStatusText = ((await page.locator('.action-bar .el-tag').first().textContent().catch(() => '')) || '').trim()
    detailActionButtons = (await page.locator('.action-bar .el-button').allTextContents().catch(() => []))
    await page.screenshot({ path: path.join(OUT_DIR, detailShot), fullPage: true })
  }
  const carbonNeutralProbe = {
    page: 'carbon-neutral',
    rowCount,
    firstRowText,
    actionButtons,
    detailStatusText,
    detailActionButtons,
    detailShot,
    pageText: (await page.locator('body').textContent().catch(() => '')) || '',
  }
  writeJson('carbon-neutral-probe-2026-05-24.json', carbonNeutralProbe)
})

test('probe user profile for signature management exposure', async ({ page }) => {
  test.setTimeout(30_000)
  ensureOutDir()
  await login(page)

  await openTab(page, `${BASE_URL}/enterprise/user/profile`, 'user-profile-page-2026-05-24-v2.png')
  const profileButtons = await page.locator('.profile-page .el-button').allTextContents().catch(() => [])
  const signatureTextPresent = /signature|签名|keypair|密钥/i.test((await page.locator('body').textContent().catch(() => '')) || '')
  const profileProbe = {
    page: 'user-profile',
    profileButtons,
    signatureTextPresent,
    pageText: (await page.locator('body').textContent().catch(() => '')) || '',
  }
  writeJson('user-profile-probe-2026-05-24.json', profileProbe)
})
