import { test, type APIRequestContext, type Page } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

const USER = { username: 'enterprise001', password: 'admin123' }

type ProbeStatus = 'PASS' | 'FAIL'
interface ProbeResult {
  id: string
  module: string
  status: ProbeStatus
  evidence: string
  actual: string
}

const runId = new Date().toISOString().replace(/[:.]/g, '-')
const outDir = path.join(process.cwd(), 'test-results', `oaiss-bug-stats-${runId}`)
const shotDir = path.join(outDir, 'screenshots')
const results: ProbeResult[] = []
const PROBE_TIMEOUT_MS = 25_000

function ensureDirs() {
  fs.mkdirSync(shotDir, { recursive: true })
}

async function shot(page: Page, name: string): Promise<string> {
  const safe = name.replace(/[^a-zA-Z0-9_-]+/g, '-')
  const file = path.join(shotDir, `${safe}.png`)
  await page.screenshot({ path: file, fullPage: true })
  return path.relative(outDir, file).replace(/\\/g, '/')
}

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(300)
}

async function apiLogin(request: APIRequestContext): Promise<{ accessToken: string; refreshToken: string }> {
  const r = await request.post(`${API_BASE}/auth/login`, {
    data: { username: USER.username, password: USER.password },
  })
  const body = await r.json()
  if (!r.ok() || !body?.data?.accessToken) throw new Error(`login failed: ${r.status()}`)
  return body.data
}

async function loginAsEnterprise(page: Page, request: APIRequestContext) {
  const tokens = await apiLogin(request)
  await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' })
  await page.evaluate(({ accessToken, refreshToken }) => {
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    sessionStorage.setItem('access_token', accessToken)
    sessionStorage.setItem('refresh_token', refreshToken)
    localStorage.setItem('remember_me', 'true')
  }, tokens)
}

async function record(id: string, module: string, page: Page, fn: () => Promise<string>) {
  let status: ProbeStatus = 'PASS'
  let actual = ''
  try {
    actual = await Promise.race<string>([
      fn(),
      new Promise<string>((_, reject) => {
        setTimeout(() => reject(new Error(`probe timeout after ${PROBE_TIMEOUT_MS}ms`)), PROBE_TIMEOUT_MS)
      }),
    ])
  } catch (e) {
    status = 'FAIL'
    actual = e instanceof Error ? e.message : String(e)
  }
  const evidence = await shot(page, id)
  results.push({ id, module, status, evidence, actual })
}

function writeReport() {
  const jsonPath = path.join(outDir, 'bug-stats-report.json')
  fs.writeFileSync(jsonPath, JSON.stringify({ runId, results }, null, 2))
}

test('bug stats probe', async ({ page, request }) => {
  test.setTimeout(8 * 60 * 1000)
  ensureDirs()
  try {
    await loginAsEnterprise(page, request)

    await record('B1', 'Dashboard', page, async () => {
    await page.goto(`${BASE_URL}/enterprise/company/dashboard`)
    await settle(page)
    const charts = await page.locator('.chart-box').count()
    const cards = await page.locator('.overview-card').count()
    if (charts < 1) throw new Error(`chart-box count=${charts}`)
    if (cards < 1) throw new Error(`overview-card count=${cards}`)
    return `charts=${charts}; cards=${cards}`
  })

    await record('B2', 'Carbon Formula Generation', page, async () => {
    await page.goto(`${BASE_URL}/enterprise/carbon-formula`)
    await settle(page)
    const inputs = page.locator('input[role="spinbutton"]')
    const c = await inputs.count()
    if (c < 8) throw new Error(`spinbutton count too low: ${c}`)
    for (let i = 0; i < 8; i += 1) await inputs.nth(i).fill(i % 4 === 3 ? '0.9' : '1')
    await page.locator('.el-button--primary').first().click()
    await page.waitForTimeout(1800)
    const resultCount = await page.locator('.el-descriptions').count()
    if (resultCount < 1) throw new Error('no result descriptions')
    return `resultDescriptions=${resultCount}`
  })

    await record('B3', 'Carbon Formula Grid', page, async () => {
    const tab = page.locator('.el-tabs__item').nth(1)
    await tab.click()
    await settle(page)
    const inputs = page.locator('input[role="spinbutton"]')
    const c = await inputs.count()
    if (c < 6) throw new Error(`spinbutton count too low: ${c}`)
    for (let i = 0; i < Math.min(c, 7); i += 1) await inputs.nth(i).fill(i === 1 ? '0.1' : '1')
    await page.locator('.el-button--primary').first().click()
    await page.waitForTimeout(1800)
    const resultCount = await page.locator('.el-descriptions').count()
    if (resultCount < 1) throw new Error('no result descriptions')
    return `resultDescriptions=${resultCount}`
  })

    await record('B4', 'Market Prediction', page, async () => {
    const consoleErrors: string[] = []
    const onConsole = (msg: { type: () => string; text: () => string }) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text())
    }
    page.on('console', onConsole as never)
    await page.goto(`${BASE_URL}/enterprise/market-prediction`)
    await settle(page)
    await page.waitForTimeout(1500)
    const hasNullMapError = consoleErrors.some(e => e.includes('reading') && e.includes('map'))
    page.off('console', onConsole as never)
    if (hasNullMapError) throw new Error('console has null map runtime error')
    return `consoleErrors=${consoleErrors.length}`
  })

    await record('B5', 'P2P API', page, async () => {
    const tokens = await apiLogin(request)
    const res = await request.post(`${API_BASE}/trade/p2p`, {
      headers: { Authorization: `Bearer ${tokens.accessToken}` },
      data: { tradeType: 2, quantity: 1, unitPrice: 1, remark: 'bug stats probe' },
    })
    const txt = await res.text()
    if (res.status() >= 500) throw new Error(`HTTP ${res.status()} ${txt.slice(0, 240)}`)
    return `status=${res.status()}`
  })

  } finally {
    writeReport()
  }
})
