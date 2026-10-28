import { test, type APIRequestContext, type Page } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

const USERS = {
  enterprise: { username: 'enterprise001', password: 'admin123' },
}

const runId = new Date().toISOString().replace(/[:.]/g, '-')
const outDir = path.join(process.cwd(), 'test-results', `oaiss-focus-debug-${runId}`)
const shotDir = path.join(outDir, 'screenshots')
const logFile = path.join(outDir, 'focus-debug-log.md')

function ensureDirs() {
  fs.mkdirSync(shotDir, { recursive: true })
}

function append(line: string) {
  fs.appendFileSync(logFile, `${line}\n`, 'utf8')
}

async function shot(page: Page, name: string) {
  const safe = name.replace(/[^a-zA-Z0-9_-]+/g, '-')
  const file = path.join(shotDir, `${safe}.png`)
  await page.screenshot({ path: file, fullPage: true })
  append(`- screenshot: \`screenshots/${safe}.png\``)
}

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(200)
}

async function fillFirstVisible(pageOrLocator: Page | ReturnType<Page['locator']>, selectors: string[], value: string): Promise<string> {
  for (const selector of selectors) {
    const node = pageOrLocator.locator(selector).first()
    if ((await node.count()) > 0 && (await node.isVisible().catch(() => false))) {
      await node.fill(value)
      return selector
    }
  }
  throw new Error(`No visible field matched selectors: ${selectors.join(' | ')}`)
}

async function apiLogin(request: APIRequestContext): Promise<{ accessToken: string; refreshToken: string }> {
  const r = await request.post(`${API_BASE}/auth/login`, {
    data: { username: USERS.enterprise.username, password: USERS.enterprise.password },
  })
  const text = await r.text()
  const body = text ? JSON.parse(text) : {}
  if (!r.ok() || !body?.data?.accessToken) throw new Error(`login failed: ${r.status()} ${text}`)
  return body.data
}

async function loginAsEnterprise(page: Page, request: APIRequestContext) {
  const tokens = await apiLogin(request)
  await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' })
  await page.evaluate(({ accessToken, refreshToken }) => {
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    localStorage.setItem('remember_me', 'true')
    sessionStorage.setItem('access_token', accessToken)
    sessionStorage.setItem('refresh_token', refreshToken)
  }, tokens)
}

test('focus debug: S1-03 / S3 / S4', async ({ page, request }) => {
  test.setTimeout(10 * 60 * 1000)
  ensureDirs()
  append(`# OAISS Focus Debug`)
  append(`- runId: \`${runId}\``)
  append(`- base: \`${BASE_URL}\``)
  append(`- api: \`${API_BASE}\``)
  append('')

  // S1-03
  append('## S1-03 create report')
  await loginAsEnterprise(page, request)
  await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
  await settle(page)
  await shot(page, 's1-before-open')
  await page.getByRole('button', { name: /create|创建/i }).first().click()
  await settle(page)
  const d1 = page.locator('.el-dialog:visible').first()
  const d1Count = await page.locator('.el-dialog:visible').count()
  append(`- visible dialogs after open: ${d1Count}`)
  const usedPeriod = await fillFirstVisible(d1, [
    'input[placeholder*="period" i]',
    'input[placeholder*="核算"]',
    'input[placeholder*="日期"]',
    '.el-form-item input',
  ], '2026-05-22')
  const usedTitle = await fillFirstVisible(d1, [
    'input[placeholder*="title" i]',
    'input[placeholder*="标题"]',
    '.el-form-item input:nth-of-type(2)',
  ], `focus-debug-${Date.now()}`)
  const usedEmission = await fillFirstVisible(d1, [
    'textarea[placeholder*="json" i]',
    'textarea[placeholder*="排放"]',
    '.el-form-item textarea',
  ], '{"scope1":1,"scope2":2}')
  append(`- used selectors: period=${usedPeriod}, title=${usedTitle}, emission=${usedEmission}`)
  const textareas = d1.locator('textarea')
  if ((await textareas.count()) > 1) {
    await textareas.nth(1).fill('focus debug')
  }
  await d1.locator('.el-dialog__footer .el-button--primary').click()
  await page.waitForTimeout(1200)
  const d1After = await page.locator('.el-dialog:visible').count()
  const d1Errs = await d1.locator('.el-form-item__error').allTextContents()
  append(`- visible dialogs after submit: ${d1After}`)
  append(`- form errors: ${JSON.stringify(d1Errs)}`)
  await shot(page, 's1-after-submit')

  // S3
  append('')
  append('## S3 auction dialog')
  await page.goto(`${BASE_URL}/enterprise/trading/market`)
  await settle(page)
  await shot(page, 's3-before-open')
  await page.locator('.search-row .el-button--success').click()
  await settle(page)
  const d3 = page.locator('.el-dialog:visible').first()
  append(`- dialog visible count: ${await page.locator('.el-dialog:visible').count()}`)
  append(`- select wrapper count: ${await d3.locator('.el-select__wrapper, .el-select').count()}`)
  await d3.locator('.el-select__wrapper, .el-select').first().click()
  await page.waitForTimeout(300)
  append(`- visible dropdown items: ${await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()}`)
  await shot(page, 's3-dropdown-open')

  // S4 + backend repro
  append('')
  append('## S4 p2p dialog + API')
  await page.goto(`${BASE_URL}/enterprise/trading/p2p`)
  await settle(page)
  await page.locator('.search-form .el-button--success').click()
  await settle(page)
  const d4 = page.locator('.el-dialog:visible').first()
  await d4.locator('.el-input-number input').nth(0).fill('1')
  await d4.locator('.el-input-number input').nth(1).fill('1')
  await d4.locator('textarea').fill('focus debug p2p')
  await d4.locator('.el-dialog__footer .el-button--primary').click()
  await page.waitForTimeout(1200)
  append(`- p2p dialog visible after submit: ${await page.locator('.el-dialog:visible').count()}`)
  append(`- p2p form errors: ${JSON.stringify(await d4.locator('.el-form-item__error').allTextContents())}`)
  await shot(page, 's4-after-submit')

  const tokens = await apiLogin(request)
  const p2pRes = await request.post(`${API_BASE}/trade/p2p`, {
    headers: { Authorization: `Bearer ${tokens.accessToken}` },
    data: { tradeType: 2, quantity: 1, unitPrice: 1, remark: 'focus api debug' },
  })
  const p2pBody = await p2pRes.text()
  append(`- api /trade/p2p status: ${p2pRes.status()}`)
  append(`- api /trade/p2p body: ${p2pBody.slice(0, 400)}`)

  append('')
  append('done.')
})
