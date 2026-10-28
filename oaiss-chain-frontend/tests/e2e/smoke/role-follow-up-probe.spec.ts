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
  await page.waitForTimeout(900)
}

test('follow-up role discovery probe', async ({ page }) => {
  test.setTimeout(120_000)
  ensureOutDir()

  const reviewerRequests: string[] = []
  page.on('request', (req) => {
    const url = req.url()
    if (url.includes('/api/v1/reviewer/') || url.includes('/api/v1/carbon/reports')) {
      reviewerRequests.push(`${req.method()} ${url}`)
    }
  })

  await loginViaApi(page, 'reviewer001', 'admin123')
  await page.goto(`${BASE_URL}/auditor/audit/list`)
  await settle(page)

  const tabs = page.locator('.el-tabs__item')
  const tabTexts = await tabs.allTextContents().catch(() => [])
  if ((await tabs.count()) > 1) {
    await tabs.nth(1).click().catch(() => {})
    await settle(page)
  }

  const allStatuses = await page.locator('.el-table__body tbody tr td:nth-child(5)').allTextContents().catch(() => [])
  const allOps = await page.locator('.el-table__body tbody tr td:last-child').allTextContents().catch(() => [])

  let clickedStatus = ''
  let dialogVisible = false
  const rows = page.locator('.el-table__body tbody tr')
  const rowCount = await rows.count().catch(() => 0)
  for (let i = 0; i < rowCount; i += 1) {
    const statusText = ((await rows.nth(i).locator('td').nth(4).textContent().catch(() => '')) || '').trim()
    if (/已上链|涓婇摼|on.?chain/i.test(statusText)) {
      clickedStatus = statusText
      const op = rows.nth(i).locator('button, .el-button').last()
      if (await op.count()) {
        await op.click().catch(() => {})
        await settle(page)
        dialogVisible = await page.locator('.el-dialog').filter({ has: page.locator('.el-radio-group, textarea, .el-form') }).count().then(n => n > 0).catch(() => false)
      }
      break
    }
  }

  await page.screenshot({ path: path.join(OUT_DIR, 'reviewer-follow-up-2026-05-25.png'), fullPage: true })
  writeJson('reviewer-follow-up-2026-05-25.json', {
    url: page.url(),
    tabTexts,
    requests: reviewerRequests,
    allStatuses,
    allOps,
    clickedStatus,
    dialogVisible,
  })

  await loginViaApi(page, 'thirdparty001', 'admin123')
  const thirdPartyRequests: string[] = []
  page.removeAllListeners('request')
  page.on('request', (req) => {
    const url = req.url()
    if (url.includes('/api/v1/third-party/')) {
      thirdPartyRequests.push(`${req.method()} ${url}`)
    }
  })
  await page.goto(`${BASE_URL}/third-party/monitor`)
  await settle(page)
  const monitorProbe = {
    url: page.url(),
    requests: thirdPartyRequests,
    filterInputCount: await page.locator('.search-form input, .search-form .el-select, .el-form input, .el-form .el-select').count().catch(() => 0),
    bodyHasOrgInfoText: await page.locator('body').textContent().then((t) => /机构信息|组织信息|联系方式|contact|org info/i.test(t || '')).catch(() => false),
    menuTexts: await page.locator('.el-menu-item, .el-sub-menu__title').allTextContents().catch(() => []),
  }
  await page.screenshot({ path: path.join(OUT_DIR, 'third-party-follow-up-2026-05-25.png'), fullPage: true })
  writeJson('third-party-follow-up-2026-05-25.json', monitorProbe)

  await loginViaApi(page, 'admin', 'admin123')
  const adminRequests: string[] = []
  page.removeAllListeners('request')
  page.on('request', (req) => {
    const url = req.url()
    if (url.includes('/api/v1/admin/')) {
      adminRequests.push(`${req.method()} ${url}`)
    }
  })
  await page.goto(`${BASE_URL}/admin/system/config`)
  await settle(page)
  const configProbe = {
    url: page.url(),
    requests: adminRequests,
    hasConfigRequest: adminRequests.some((entry) => entry.includes('/admin/config')),
    rowTexts: await page.locator('.el-table__body tbody tr').allTextContents().catch(() => []),
    localStorageKeys: await page.evaluate(() => Object.keys(window.localStorage).filter((key) => key.includes('config') || key.includes('oaiss'))),
  }
  await page.screenshot({ path: path.join(OUT_DIR, 'admin-config-follow-up-2026-05-25.png'), fullPage: true })
  writeJson('admin-config-follow-up-2026-05-25.json', configProbe)

  expect(true).toBeTruthy()
})
