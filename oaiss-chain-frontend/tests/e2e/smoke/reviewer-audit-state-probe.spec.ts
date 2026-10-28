import { test, expect, type Page } from '@playwright/test'
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
  await page.waitForTimeout(1000)
}

test('probe reviewer audit list and history state closures', async ({ page }) => {
  test.setTimeout(120_000)
  ensureOutDir()
  await loginViaApi(page, 'reviewer001', 'admin123')

  let reviewerStatsData: Record<string, any> | null = null
  let reviewerInfoData: Record<string, unknown> | null = null

  page.on('response', async (response) => {
    const url = response.url()
    if (!response.ok()) return

    if (url.includes('/api/v1/reviewer/statistics')) {
      const body = await response.json().catch(() => null)
      reviewerStatsData = body?.data ?? null
    }

    if (url.includes('/api/v1/reviewer/info')) {
      const body = await response.json().catch(() => null)
      reviewerInfoData = body?.data ?? null
    }
  })

  await page.goto(`${BASE_URL}/auditor/audit/list`)
  await settle(page)

  const pendingEnterpriseCells = (await page.locator('.el-table__body tbody tr td:nth-child(2)').allTextContents().catch(() => []))
    .map((text) => text.trim())
    .filter(Boolean)
  const pendingStatusCells = (await page.locator('.el-table__body tbody tr td:nth-child(5)').allTextContents().catch(() => []))
    .map((text) => text.trim())
    .filter(Boolean)
  const pendingOperationTexts = (await page.locator('.el-table__body tbody tr td:last-child').allTextContents().catch(() => []))
    .map((text) => text.trim())
    .filter(Boolean)

  const cardTexts = (await page.locator('.section-card').allTextContents().catch(() => []))
    .map((text) => text.replace(/\s+/g, ' ').trim())
  const qualificationCardText = cardTexts[0] || ''
  const statisticsCardText = cardTexts[1] || ''

  const tabs = page.locator('.el-tabs__item')
  if ((await tabs.count()) > 1) {
    await tabs.nth(1).click().catch(() => {})
    await settle(page)
  }

  const allStatuses = (await page.locator('.el-table__body tbody tr td:nth-child(5)').allTextContents().catch(() => []))
    .map((text) => text.trim())
    .filter(Boolean)
  const allOps = (await page.locator('.el-table__body tbody tr td:last-child').allTextContents().catch(() => []))
    .map((text) => text.trim())
    .filter(Boolean)

  await page.goto(`${BASE_URL}/auditor/review/history`)
  await settle(page)

  const historyRows = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
  const historyEmptyVisible = await page.locator('.el-empty').count().then((n) => n > 0).catch(() => false)

  const probe = {
    qualificationCardText,
    statisticsCardText,
    reviewerStatsData,
    reviewerInfoData,
    pendingEnterpriseCells,
    pendingStatusCells,
    pendingOperationTexts,
    allStatuses,
    allOps,
    historyRows,
    historyEmptyVisible,
  }

  writeJson('reviewer-audit-state-probe-2026-05-26.json', probe)
  await page.screenshot({
    path: path.join(OUT_DIR, 'reviewer-audit-state-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(pendingEnterpriseCells.length).toBeGreaterThan(0)
  expect(pendingEnterpriseCells.every((text) => text !== '-' && text !== '--')).toBe(true)
  expect(pendingStatusCells.length).toBeGreaterThan(0)
  expect(pendingStatusCells.every((text) => text !== '-' && text !== '--')).toBe(true)
  expect(pendingOperationTexts.some((text) => text !== '-')).toBe(true)
  expect(allOps.every((text) => text === '-')).toBe(true)
  expect(historyRows).toBeGreaterThan(0)
  expect(historyEmptyVisible).toBe(false)
  expect(Number((reviewerStatsData as any)?.completedReviews ?? 0)).toBeGreaterThan(0)
  expect(qualificationCardText.length).toBeGreaterThan(0)
})
