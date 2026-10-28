import { test, expect, type Page, type Request } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const OUT_DIR = path.join(process.cwd(), 'test-results', 'ad-hoc-probes')

type TableSnapshot = {
  rows: number
  totalText: string
  tableTipText: string
  firstTradeNo: string
  tradeNos: string[]
  emptyVisible: boolean
}

type SeedTradeResult = {
  created: boolean
  id?: number
  tradeNo?: string
  status?: number
  body?: unknown
}

type SeedLoginResult = {
  token: string
  refreshToken: string
  userId: number
  username: string
}

type TradeListResponseSnapshot = {
  url: string
  status: number
  totalElements: number | null
  rowCount: number | null
  body: unknown
}

function ensureOutDir() {
  fs.mkdirSync(OUT_DIR, { recursive: true })
}

function writeJson(name: string, data: unknown) {
  fs.writeFileSync(path.join(OUT_DIR, path.basename(name)), JSON.stringify(data, null, 2), 'utf8')
}

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(800)
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

async function waitForTradeTableIdle(page: Page) {
  await page.waitForFunction(() => {
    const masks = Array.from(document.querySelectorAll('.el-loading-mask'))
    const visibleMasks = masks.filter((node) => {
      if (!(node instanceof HTMLElement)) return false
      const style = window.getComputedStyle(node)
      return !node.classList.contains('is-hidden')
        && style.display !== 'none'
        && style.visibility !== 'hidden'
        && style.opacity !== '0'
    })
    return visibleMasks.length === 0
  }, undefined, { timeout: 15000 })
}

async function waitForTradeTableOrEmpty(page: Page) {
  await page.waitForFunction(() => {
    const rows = document.querySelectorAll('.el-table__body tbody tr').length
    const empty = Array.from(document.querySelectorAll('.el-table__empty-text'))
      .some((node) => (node.textContent || '').trim().length > 0)
    return rows > 0 || empty
  }, undefined, { timeout: 15000 })
}

async function readTotalText(page: Page): Promise<string> {
  const candidates = [
    '.el-pagination__total',
    '.el-pagination .is-first + *',
  ]
  for (const selector of candidates) {
    const locator = page.locator(selector).first()
    if (await locator.count()) {
      const text = (await locator.textContent())?.trim()
      if (text) return text
    }
  }
  return ''
}

async function readOptionalText(page: Page, selector: string): Promise<string> {
  const locator = page.locator(selector).first()
  if (!await locator.count()) {
    return ''
  }
  return ((await locator.textContent()) || '').trim()
}

async function snapshotTable(page: Page): Promise<TableSnapshot> {
  await waitForTradeTableIdle(page)
  await waitForTradeTableOrEmpty(page)
  const rows = page.locator('.el-table__body tbody tr')
  const rowCount = await rows.count()
  const tradeNoCells = page.locator('.el-table__body tbody tr td:nth-child(2), .el-table__body tbody tr td:nth-child(3)')
  const texts = (await tradeNoCells.allTextContents())
    .map((item) => item.trim())
    .filter((item) => /^TR|^\d{6,}|^trade_/i.test(item) || item.includes('TRADE') || item.includes('-'))
  const tradeNos = [...new Set(texts)].slice(0, 8)
  return {
    rows: rowCount,
    totalText: await readTotalText(page),
    tableTipText: await readOptionalText(page, '.table-tip'),
    firstTradeNo: tradeNos[0] || '',
    tradeNos,
    emptyVisible: await page.locator('.el-table__empty-text').first().isVisible().catch(() => false),
  }
}

async function fillSearchFormInput(page: Page, formItemIndex: number, value: string) {
  const input = page
    .locator('.search-form .el-form-item')
    .nth(formItemIndex)
    .locator('.el-input input')
    .first()
  await input.waitFor({ state: 'visible', timeout: 10000 })
  await input.click()
  await input.fill(value)
}

async function fillP2PTradeNoInput(page: Page, value: string) {
  const input = page.getByRole('textbox', { name: '交易编号' })
  await input.waitFor({ state: 'visible', timeout: 15000 })
  await input.click()
  await input.fill(value)
}

async function clickSearch(page: Page) {
  await page.locator('.search-form .el-button--primary').first().click()
}

async function persistAuth(page: Page, accessToken: string, refreshToken: string) {
  await page.goto(BASE_URL)
  await page.evaluate(
    ({ nextAccessToken, nextRefreshToken }) => {
      localStorage.setItem('access_token', nextAccessToken)
      localStorage.setItem('refresh_token', nextRefreshToken)
      localStorage.setItem('remember_me', 'true')
    },
    { nextAccessToken: accessToken, nextRefreshToken: refreshToken },
  )
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
    refreshToken: String(body.data.refreshToken),
    userId: Number(body.data.userId),
    username: String(body.data.username || username),
  }
}

async function createAuctionSeedTrade(page: Page, token: string): Promise<SeedTradeResult> {
  const response = await page.request.post(`${API_BASE_URL}/trade/auction`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: {
      tradeType: 1,
      quantity: 1,
      unitPrice: 1,
      remark: `trade-filter-probe-${Date.now()}`,
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
    id: body?.data?.id,
    tradeNo: body?.data?.tradeNo,
    status: response.status(),
    body,
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
    id: body?.data?.id,
    tradeNo: body?.data?.tradeNo,
    status: response.status(),
    body,
  }
}

async function captureTradeListResponse(
  page: Page,
  action: () => Promise<void>,
  matcher?: (url: string) => boolean,
): Promise<{ urls: string[]; response: TradeListResponseSnapshot }> {
  const responsePromise = page.waitForResponse((response) => {
    const url = response.url()
    return url.includes('/api/v1/trade/my-trades') && (!matcher || matcher(url))
  }, { timeout: 15000 })

  const urls = await captureTradeRequests(page, action)
  const response = await responsePromise
  const body = await response.json().catch(() => null)

  return {
    urls,
    response: {
      url: response.url(),
      status: response.status(),
      totalElements: typeof body?.data?.totalElements === 'number' ? body.data.totalElements : null,
      rowCount: Array.isArray(body?.data?.content) ? body.data.content.length : null,
      body,
    },
  }
}

async function openP2PPage(page: Page) {
  await page.goto(`${BASE_URL}/enterprise/trading/p2p`)
  await settle(page)
  await waitForTradeTableIdle(page)
  await waitForTradeTableOrEmpty(page)
}

function assertSeedTradeCreated(seedTrade: SeedTradeResult, label: string) {
  expect(seedTrade.created, `${label} seed trade should be created`).toBeTruthy()
  expect(seedTrade.tradeNo, `${label} seed trade should return a tradeNo`).toBeTruthy()
}

test('probe enterprise orders filters against real backend behavior', async ({ page }) => {
  test.setTimeout(45_000)
  ensureOutDir()
  const enterpriseUser = await loginForSeed(page, 'enterprise001', 'admin123')
  const seededTrade = await createAuctionSeedTrade(page, enterpriseUser.token)
  await persistAuth(page, enterpriseUser.token, enterpriseUser.refreshToken)

  await page.goto(`${BASE_URL}/enterprise/orders/manage`)
  await settle(page)
  const ordersBefore = await snapshotTable(page)

  const impossibleTradeNo = `NONEXISTENT_QA_${Date.now()}`
  const tradeNoCycle = await captureTradeListResponse(page, async () => {
    await fillSearchFormInput(page, 0, impossibleTradeNo)
    await clickSearch(page)
  }, (url) => url.includes('tradeNo='))
  await settle(page)
  const ordersAfterTradeNo = await snapshotTable(page)

  await page.locator('.search-form .el-button').nth(1).click()
  await settle(page)
  await waitForTradeTableIdle(page)
  await waitForTradeTableOrEmpty(page)

  const dateInput = page.locator('.search-form .el-range-input').first()
  await dateInput.click()
  await page.keyboard.press('Control+a')
  await page.keyboard.type('2099-01-01')
  const endDateInput = page.locator('.search-form .el-range-input').nth(1)
  await endDateInput.click()
  await page.keyboard.press('Control+a')
  await page.keyboard.type('2099-01-02')
  const dateCycle = await captureTradeListResponse(page, async () => {
    await clickSearch(page)
  }, (url) => url.includes('startTime=') || url.includes('endTime='))
  await settle(page)
  const ordersAfterDate = await snapshotTable(page)
  await page.screenshot({
    path: path.join(OUT_DIR, 'orders-manage-impossible-tradeno-filter-2026-05-31.png'),
    fullPage: true,
  })
  const ordersProbe = {
    probe: 'orders-manage',
    seededTrade,
    before: ordersBefore,
    impossibleTradeNo,
    afterTradeNo: ordersAfterTradeNo,
    tradeNoUrls: tradeNoCycle.urls,
    tradeNoResponse: tradeNoCycle.response,
    afterDateRange: ordersAfterDate,
    dateRangeUrls: dateCycle.urls,
    dateRangeResponse: dateCycle.response,
  }
  writeJson('orders-manage-filter-probe-2026-05-31.json', ordersProbe)
  console.log(JSON.stringify(ordersProbe, null, 2))

  if (!seededTrade.created && ordersBefore.rows === 0) {
    expect(ordersAfterTradeNo.rows).toBe(0)
    expect(ordersAfterDate.rows).toBe(0)
    expect(tradeNoCycle.urls.some((url) => url.includes('tradeNo='))).toBeTruthy()
    expect(dateCycle.urls.some((url) => url.includes('startTime=') || url.includes('endTime='))).toBeTruthy()
    expect(dateCycle.response.totalElements).toBe(0)
    return
  }

  expect(ordersBefore.rows).toBeGreaterThan(0)
  expect(`${ordersBefore.totalText} ${ordersBefore.tableTipText}`).toMatch(/\d+/)
  expect(ordersAfterTradeNo.rows).toBe(0)
  expect(ordersAfterDate.rows).toBe(0)
  expect(tradeNoCycle.urls.some((url) => url.includes('tradeNo='))).toBeTruthy()
  expect(dateCycle.urls.some((url) => url.includes('startTime=') || url.includes('endTime='))).toBeTruthy()
  expect(dateCycle.response.totalElements).toBe(0)
})

test('probe trading p2p filters against real backend behavior', async ({ page }) => {
  test.setTimeout(120_000)
  ensureOutDir()

  const enterpriseUser = await loginForSeed(page, 'enterprise001', 'admin123')
  const counterpartyUser = await loginForSeed(page, 'enterprise002', 'admin123')

  const sellerSeed = await createP2PSeedTrade(
    page,
    enterpriseUser.token,
    counterpartyUser.userId,
    `trade-filter-seller-${Date.now()}`,
  )
  const buyerSeed = await createP2PSeedTrade(
    page,
    counterpartyUser.token,
    enterpriseUser.userId,
    `trade-filter-buyer-${Date.now()}`,
  )

  assertSeedTradeCreated(sellerSeed, 'seller-side')
  assertSeedTradeCreated(buyerSeed, 'buyer-side')

  await persistAuth(page, enterpriseUser.token, enterpriseUser.refreshToken)

  await openP2PPage(page)
  const p2pBefore = await snapshotTable(page)

  await fillP2PTradeNoInput(page, sellerSeed.tradeNo!)
  const p2pTradeNoUrls = await captureTradeRequests(page, async () => {
    await clickSearch(page)
  })
  await settle(page)
  await waitForTradeTableOrEmpty(page)
  const p2pAfterTradeNo = await snapshotTable(page)

  await page.screenshot({
    path: path.join(OUT_DIR, 'p2p-filter-live-probe-2026-05-31.png'),
    fullPage: true,
  })

  const p2pProbe = {
    probe: 'trading-p2p',
    enterpriseUser,
    counterpartyUser,
    sellerSeed,
    buyerSeed,
    before: p2pBefore,
    sellerTradeNo: sellerSeed.tradeNo,
    afterTradeNo: p2pAfterTradeNo,
    tradeNoUrls: p2pTradeNoUrls,
  }
  writeJson('trading-p2p-filter-probe-2026-05-31.json', p2pProbe)
  console.log(JSON.stringify(p2pProbe, null, 2))

  expect(p2pBefore.rows).toBeGreaterThan(0)
  expect(p2pTradeNoUrls.some((url) => url.includes('tradeNo='))).toBeTruthy()
  expect(p2pAfterTradeNo.rows).toBeGreaterThan(0)
  expect(p2pAfterTradeNo.tradeNos).toContain(sellerSeed.tradeNo!)
  expect(p2pAfterTradeNo.tradeNos).not.toContain(buyerSeed.tradeNo!)
})
