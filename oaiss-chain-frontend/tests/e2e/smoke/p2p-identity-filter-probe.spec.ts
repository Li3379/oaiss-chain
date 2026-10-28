import { test, expect, type Page, type Request } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { loginViaApi } from '../fixtures/auth'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const OUT_DIR = path.join(process.cwd(), 'test-results', 'ad-hoc-probes')

type TableSnapshot = {
  rows: number
  tradeNos: string[]
}

type SeedTradeResult = {
  created: boolean
  tradeNo?: string
  status?: number
  body?: unknown
}

type SeedLoginResult = {
  token: string
  userId: number
}

type IdentityCaseResult = {
  snapshot: TableSnapshot
  urls: string[]
}

function ensureOutDir() {
  fs.mkdirSync(OUT_DIR, { recursive: true })
}

function writeJson(name: string, data: unknown) {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(data, null, 2), 'utf8')
}

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(800)
}

async function waitForTradeTableOrEmpty(page: Page) {
  await page.waitForFunction(() => {
    const rows = document.querySelectorAll('.el-table__body tbody tr').length
    const empty = Array.from(document.querySelectorAll('.el-table__empty-text'))
      .some((node) => (node.textContent || '').trim().length > 0)
    return rows > 0 || empty
  }, undefined, { timeout: 15000 })
}

async function captureTradeRequests(page: Page, action: () => Promise<void>): Promise<string[]> {
  const urls: string[] = []
  const handler = (request: Request) => {
    const url = request.url()
    if (url.includes('/api/v1/trade/my-trades')) {
      urls.push(url)
    }
  }
  page.on('request', handler)
  try {
    await action()
    await page.waitForTimeout(1200)
    return urls
  } finally {
    page.off('request', handler)
  }
}

async function loginForSeed(page: Page, username: string, password: string): Promise<SeedLoginResult> {
  const response = await page.request.post(`${API_BASE_URL}/auth/login`, {
    headers: { 'Content-Type': 'application/json' },
    data: { username, password },
  })
  const body = await response.json().catch(() => null)
  if (!response.ok() || body?.code !== 200 || !body?.data?.accessToken || !body?.data?.userId) {
    throw new Error(`Seed login failed for ${username}: HTTP ${response.status()} ${JSON.stringify(body)}`)
  }
  return {
    token: String(body.data.accessToken),
    userId: Number(body.data.userId),
  }
}

async function createP2PSeedTrade(
  page: Page,
  token: string,
  buyerId: number,
  remark: string,
): Promise<SeedTradeResult> {
  const response = await page.request.post(`${API_BASE_URL}/trade/p2p`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: {
      tradeType: 2,
      buyerId,
      quantity: 1,
      unitPrice: 1,
      remark,
    },
  })

  const body = await response.json().catch(() => null)
  if (!response.ok() || body?.code !== 200) {
    return {
      created: false,
      status: response.status(),
      body,
    }
  }

  return {
    created: true,
    tradeNo: body?.data?.tradeNo,
    status: response.status(),
    body,
  }
}

async function openP2PPage(page: Page) {
  await page.goto(`${BASE_URL}/enterprise/trading/p2p`)
  await settle(page)
  await waitForTradeTableOrEmpty(page)
}

async function fillTradeNo(page: Page, tradeNo: string) {
  const input = page.locator('.search-form input:not([readonly]):visible').nth(1)
  await input.waitFor({ state: 'visible', timeout: 10000 })
  await input.click()
  await input.fill(tradeNo)
}

async function selectIdentity(page: Page, optionIndex: number) {
  const select = page.locator('.search-form .el-select').first()
  await select.click()
  const options = page.locator('.el-select-dropdown:visible .el-select-dropdown__item:not(.is-disabled)')
  await options.nth(optionIndex).click()
}

async function clickSearch(page: Page) {
  await page.locator('.search-form .el-button--primary').first().click()
}

async function snapshotTable(page: Page): Promise<TableSnapshot> {
  const rows = page.locator('.el-table__body tbody tr')
  const rowCount = await rows.count()
  const tradeNoCells = page.locator('.el-table__body tbody tr td:nth-child(2)')
  const tradeNos = [...new Set((await tradeNoCells.allTextContents()).map((text) => text.trim()).filter(Boolean))]
  return {
    rows: rowCount,
    tradeNos,
  }
}

async function runIdentityCase(page: Page, tradeNo: string, optionIndex: number): Promise<IdentityCaseResult> {
  await openP2PPage(page)
  await fillTradeNo(page, tradeNo)
  await selectIdentity(page, optionIndex)
  const urls = await captureTradeRequests(page, async () => {
    await clickSearch(page)
  })
  await settle(page)
  await waitForTradeTableOrEmpty(page)
  return {
    snapshot: await snapshotTable(page),
    urls,
  }
}

function assertSeedTradeCreated(seedTrade: SeedTradeResult, label: string) {
  expect(seedTrade.created, `${label} seed trade should be created`).toBeTruthy()
  expect(seedTrade.tradeNo, `${label} seed trade should return a tradeNo`).toBeTruthy()
}

test('probe p2p identity filter works against live backend', async ({ page }) => {
  test.setTimeout(60_000)
  ensureOutDir()

  const enterpriseUser = await loginForSeed(page, 'enterprise001', 'admin123')
  const counterpartyUser = await loginForSeed(page, 'enterprise002', 'admin123')

  const sellerSeed = await createP2PSeedTrade(
    page,
    enterpriseUser.token,
    counterpartyUser.userId,
    `identity-filter-seller-${Date.now()}`,
  )
  const buyerSeed = await createP2PSeedTrade(
    page,
    counterpartyUser.token,
    enterpriseUser.userId,
    `identity-filter-buyer-${Date.now()}`,
  )

  assertSeedTradeCreated(sellerSeed, 'seller-side')
  assertSeedTradeCreated(buyerSeed, 'buyer-side')

  await loginViaApi(page, 'enterprise001', 'admin123')

  const sellerMatch = await runIdentityCase(page, sellerSeed.tradeNo!, 1)
  const sellerMismatch = await runIdentityCase(page, sellerSeed.tradeNo!, 0)
  const buyerMatch = await runIdentityCase(page, buyerSeed.tradeNo!, 0)
  const buyerMismatch = await runIdentityCase(page, buyerSeed.tradeNo!, 1)

  await page.screenshot({
    path: path.join(OUT_DIR, 'p2p-identity-filter-live-2026-05-31.png'),
    fullPage: true,
  })

  const result = {
    sellerSeed,
    buyerSeed,
    sellerMatch,
    sellerMismatch,
    buyerMatch,
    buyerMismatch,
  }
  writeJson('p2p-identity-filter-probe-2026-05-31.json', result)

  for (const urls of [sellerMatch.urls, sellerMismatch.urls, buyerMatch.urls, buyerMismatch.urls]) {
    expect(urls.some((url) => url.includes('tradeNo='))).toBeTruthy()
  }

  expect(sellerMatch.urls.some((url) => url.includes('identity=seller'))).toBeTruthy()
  expect(sellerMismatch.urls.some((url) => url.includes('identity=buyer'))).toBeTruthy()
  expect(buyerMatch.urls.some((url) => url.includes('identity=buyer'))).toBeTruthy()
  expect(buyerMismatch.urls.some((url) => url.includes('identity=seller'))).toBeTruthy()

  expect(sellerMatch.snapshot.tradeNos).toContain(sellerSeed.tradeNo!)
  expect(sellerMismatch.snapshot.rows).toBe(0)
  expect(buyerMatch.snapshot.tradeNos).toContain(buyerSeed.tradeNo!)
  expect(buyerMismatch.snapshot.rows).toBe(0)
})
