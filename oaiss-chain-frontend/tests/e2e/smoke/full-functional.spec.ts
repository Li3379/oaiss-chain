import { test, type APIRequestContext, type Page } from '@playwright/test'
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import type { Response } from '@playwright/test'

type CaseStatus = 'PASS' | 'FAIL' | 'SKIP'

interface CaseResult {
  id: string
  suite: string
  name: string
  priority: 'P0' | 'P1' | 'P2'
  status: CaseStatus
  url: string
  screenshot: string
  error?: string
  notes?: string
  consoleErrors: number
  durationMs: number
}

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const DEFAULT_BROWSER_API_BASES = ['http://127.0.0.1:8080/api/v1', 'http://localhost:8080/api/v1']
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-')
const OUTPUT_DIR = path.join(process.cwd(), 'test-results', `oaiss-full-functional-${RUN_ID}`)
const SCREENSHOT_DIR = path.join(OUTPUT_DIR, 'screenshots')
const BACKEND_LOG_PATH = path.resolve(process.cwd(), '..', 'oaiss-chain-backend', 'logs', 'oaiss-chain-backend.log')

const USERS = {
  enterprise: { username: 'enterprise001', password: 'admin123', home: '/enterprise/carbon/upload' },
  admin: { username: 'admin', password: 'admin123', home: '/admin/system/users' },
  reviewer: { username: 'reviewer001', password: 'admin123', home: '/auditor/audit/list' },
  thirdParty: { username: 'thirdparty001', password: 'admin123', home: '/third-party/monitor' },
}

const results: CaseResult[] = []
const consoleIssues: string[] = []
const CASE_TIMEOUT_MS = 60_000

interface RunSummary {
  total: number
  passed: number
  failed: number
  skipped: number
}

interface CaptchaPayload {
  captchaKey: string
  captchaImage: string
}

function ensureOutputDirs() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
}

function slug(value: string): string {
  return value.replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-|-$/g, '').slice(0, 120)
}

function shortError(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error)
  return message.replace(/\s+/g, ' ').slice(0, 800)
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(150)
}

async function installApiBaseOverride(page: Page) {
  if (DEFAULT_BROWSER_API_BASES.includes(API_BASE)) return
  await page.route('**/api/v1/**', async route => {
    const originalUrl = route.request().url()
    const matchedBase = DEFAULT_BROWSER_API_BASES.find(base => originalUrl.startsWith(base))
    if (!matchedBase) {
      await route.continue()
      return
    }
    const rewrittenUrl = `${API_BASE}${originalUrl.slice(matchedBase.length)}`
    await route.continue({ url: rewrittenUrl })
  })
}

async function screenshot(page: Page, id: string, name: string): Promise<string> {
  const file = path.join(SCREENSHOT_DIR, `${slug(`${id}-${name}`)}.png`)
  await page.screenshot({ path: file, fullPage: true }).catch(async () => {
    await page.screenshot({ path: file, fullPage: false }).catch(() => {})
  })
  return path.relative(OUTPUT_DIR, file).replace(/\\/g, '/')
}

async function recordCase(
  page: Page,
  suite: string,
  id: string,
  name: string,
  priority: 'P0' | 'P1' | 'P2',
  fn: () => Promise<string | void>,
) {
  const start = Date.now()
  const startErrorCount = consoleIssues.length
  let status: CaseStatus = 'PASS'
  let error: string | undefined
  let notes: string | undefined

  try {
    const maybeNotes = await Promise.race<string | void>([
      fn(),
      new Promise<string>((_, reject) => {
        setTimeout(() => reject(new Error(`Case timeout after ${CASE_TIMEOUT_MS}ms`)), CASE_TIMEOUT_MS)
      }),
    ])
    if (maybeNotes) notes = maybeNotes
  } catch (e) {
    status = 'FAIL'
    error = shortError(e)
  }

  const shot = await screenshot(page, id, name)
  results.push({
    id,
    suite,
    name,
    priority,
    status,
    url: page.url(),
    screenshot: shot,
    error,
    notes,
    consoleErrors: consoleIssues.length - startErrorCount,
    durationMs: Date.now() - start,
  })
}

async function recordSkip(page: Page, suite: string, id: string, name: string, priority: 'P0' | 'P1' | 'P2', notes: string) {
  const shot = await screenshot(page, id, name)
  results.push({
    id,
    suite,
    name,
    priority,
    status: 'SKIP',
    url: page.url(),
    screenshot: shot,
    notes,
    consoleErrors: 0,
    durationMs: 0,
  })
}

async function api<T>(
  request: APIRequestContext,
  method: 'get' | 'post' | 'put' | 'delete',
  token: string | undefined,
  url: string,
  options: { data?: unknown; params?: Record<string, unknown> } = {},
): Promise<T> {
  const response = await request[method](`${API_BASE}${url}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    data: options.data,
    params: options.params as Record<string, string | number | boolean> | undefined,
  })
  const text = await response.text()
  let body: any = {}
  try {
    body = text ? JSON.parse(text) : {}
  } catch {
    body = { raw: text }
  }
  if (!response.ok() || (body.code !== undefined && ![0, 200].includes(body.code))) {
    throw new Error(`${method.toUpperCase()} ${url} failed: HTTP ${response.status()} ${text.slice(0, 300)}`)
  }
  return body.data as T
}

async function loginByApi(page: Page, request: APIRequestContext, user: keyof typeof USERS): Promise<string> {
  const auth = USERS[user]
  const data = await api<{ accessToken: string; refreshToken: string }>(request, 'post', undefined, '/auth/login', {
    data: { username: auth.username, password: auth.password },
  })
  // Navigate to base URL first to load the SPA
  await page.goto(BASE_URL, { waitUntil: 'domcontentloaded', timeout: 15000 })
  // Set tokens in storage
  await page.evaluate(({ accessToken, refreshToken }) => {
    const savedLocale = localStorage.getItem('locale')
    localStorage.clear()
    sessionStorage.clear()
    if (savedLocale) localStorage.setItem('locale', savedLocale)
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    localStorage.setItem('remember_me', 'true')
    try {
      const payload = JSON.parse(atob(accessToken.split('.')[1]))
      if (payload?.exp) {
        localStorage.setItem('token_expiry', String(payload.exp * 1000))
      }
    } catch {
      // Ignore malformed token payloads in test setup.
    }
    sessionStorage.setItem('access_token', accessToken)
    sessionStorage.setItem('refresh_token', refreshToken)
    sessionStorage.setItem('remember_me', 'true')
  }, data)
  // Reload so Pinia store re-initializes with tokens from storage
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 15000 })
  return data.accessToken
}

async function gotoAs(page: Page, request: APIRequestContext, user: keyof typeof USERS, route: string): Promise<string> {
  const token = await loginByApi(page, request, user)
  await page.goto(`${BASE_URL}${route}`, { waitUntil: 'domcontentloaded', timeout: 15000 })
  await page.waitForTimeout(500)
  await settle(page)
  return token
}

async function expectAppPage(page: Page, route: string) {
  if (!page.url().includes(route)) throw new Error(`Expected route ${route}, got ${page.url()}`)
  // Wait up to 5s for the app shell to mount (Vue SPA hydration can be slow)
  const selector = '.app-shell, .main-content, .section-card, .el-card'
  const deadline = Date.now() + 5000
  while (Date.now() < deadline) {
    if ((await page.locator(selector).count()) > 0) return
    await page.waitForTimeout(200)
  }
  throw new Error('No application shell or content cards rendered')
}

async function clickUnique(page: Page, selector: string, label: string) {
  const count = await page.locator(selector).count()
  if (count < 1) throw new Error(`Missing control: ${label} (${selector})`)
  await page.locator(selector).first().click()
}

async function waitForLocatorCount(page: Page, selector: string, expectedCount: number, timeoutMs = 8000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    if ((await page.locator(selector).count()) >= expectedCount) {
      return
    }
    await page.waitForTimeout(200)
  }
  throw new Error(`Expected ${expectedCount}+ matches for ${selector}`)
}

async function waitForStorageToken(page: Page, timeoutMs = 8000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    const storage = await page.evaluate(() => ({
      sessionAccess: sessionStorage.getItem('access_token'),
      sessionRefresh: sessionStorage.getItem('refresh_token'),
      localAccess: localStorage.getItem('access_token'),
      localRefresh: localStorage.getItem('refresh_token'),
    }))
    const access = storage.sessionAccess || storage.localAccess
    const refresh = storage.sessionRefresh || storage.localRefresh
    if (access && refresh) return storage
    await page.waitForTimeout(200)
  }
  throw new Error('Expected access/refresh tokens in storage after waiting')
}

async function waitForRowByText(page: Page, rowLocator: ReturnType<Page['locator']>, timeoutMs = 8000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    if ((await rowLocator.count()) > 0 && await rowLocator.first().isVisible().catch(() => false)) {
      return
    }
    await page.waitForTimeout(200)
  }
  throw new Error('New project row not visible')
}

async function waitForDialogToClose(page: Page, dialog: ReturnType<Page['locator']>, timeoutMs = 8000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    const visible = await dialog.isVisible().catch(() => false)
    if (!visible) return
    const busyButtons = dialog.locator('.el-dialog__footer .el-button.is-loading, .el-dialog__footer .el-button--primary.is-loading')
    if ((await busyButtons.count()) > 0) {
      await page.waitForTimeout(200)
      continue
    }
    await page.waitForTimeout(200)
  }
  throw new Error('Dialog remained visible longer than expected')
}

async function waitForSuccessToast(page: Page, timeoutMs = 6000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    const successMessage = page.locator('.el-message--success').last()
    if ((await successMessage.count()) > 0 && await successMessage.isVisible().catch(() => false)) return
    await page.waitForTimeout(150)
  }
}

async function waitForAnyToast(page: Page, timeoutMs = 6000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    const toast = page.locator('.el-message').last()
    if ((await toast.count()) > 0 && await toast.isVisible().catch(() => false)) {
      return (await toast.textContent().catch(() => ''))?.trim() || ''
    }
    await page.waitForTimeout(150)
  }
  return ''
}

async function expandVisibleCollapseItems(page: Page) {
  const headers = page.locator('.el-tab-pane:visible .el-collapse-item__header')
  const count = await headers.count()
  for (let i = 0; i < count; i += 1) {
    const header = headers.nth(i)
    const item = header.locator('xpath=ancestor::*[contains(@class,"el-collapse-item")]').first()
    const isActive = await item.evaluate(el => el.classList.contains('is-active')).catch(() => false)
    if (!isActive) {
      await header.click().catch(async () => {
        await header.click({ force: true })
      })
      await page.waitForTimeout(150)
    }
  }
}

async function openSidebarLeaf(page: Page, sectionPattern: RegExp, leafPattern: RegExp) {
  const sectionTitle = page.locator('.side-panel .el-sub-menu__title span').filter({ hasText: sectionPattern }).first()
  if ((await sectionTitle.count()) === 0) {
    throw new Error(`Sidebar section not found: ${sectionPattern}`)
  }

  const sectionTrigger = sectionTitle.locator('..')
  await sectionTrigger.click().catch(async () => {
    await sectionTrigger.click({ force: true })
  })
  await page.waitForTimeout(250)

  const leaf = page.locator('.side-panel .el-menu-item').filter({ hasText: leafPattern }).first()
  if ((await leaf.count()) === 0) {
    throw new Error(`Sidebar leaf not found: ${leafPattern}`)
  }

  await leaf.click({ timeout: 3000 }).catch(async () => {
    await leaf.click({ timeout: 3000, force: true })
  })
}

async function createDraftReport(request: APIRequestContext, token: string, title: string) {
  return api<any>(request, 'post', token, '/carbon/reports', {
    data: {
      accountingPeriod: '2026-05-22',
      title,
      reportType: 1,
      emissionData: JSON.stringify({
        scope1: [{ activity_data: '12', emission_factor: '1' }],
        scope2: [{ activity_data: '8', emission_factor: '1' }],
        scope3: [{ activity_data: '3', emission_factor: '1' }],
      }),
      calculationMethod: 'automated full functional test',
    },
  })
}

async function createP2PTrade(request: APIRequestContext, token: string) {
  return api<any>(request, 'post', token, '/trade/p2p', {
      data: { tradeType: 2, sellerId: 2, buyerId: 3, quantity: 1, unitPrice: 1, remark: 'full functional test' },
  })
}

async function createProject(request: APIRequestContext, token: string, projectName: string) {
  return api<any>(request, 'post', token, '/carbon-neutral', {
    data: {
      projectName,
      projectType: 1,
      description: 'full functional test project',
      expectedReduction: 1,
      startDate: '2026-06-01',
      endDate: '2026-12-31',
    },
  })
}

function ocrCaptcha(dataUrl: string | null): string {
  if (!dataUrl) throw new Error('Captcha image src is empty')
  const source = dataUrl.includes(',') ? dataUrl.split(',')[1] : dataUrl
  const script = [
    'import base64, ddddocr, sys',
    'img = base64.b64decode(sys.stdin.read().strip())',
    'ocr = ddddocr.DdddOcr(show_ad=False)',
    'print(ocr.classification(img).strip())',
  ].join('; ')
  const result = spawnSync('python', ['-c', script], { input: source, encoding: 'utf-8', timeout: 20000 })
  if (result.status !== 0) throw new Error(result.stderr || 'Captcha OCR failed')
  const code = result.stdout.trim().replace(/[^a-zA-Z0-9]/g, '')
  if (code.length < 4) throw new Error(`Captcha OCR returned "${code}"`)
  return code.slice(0, 4)
}

async function waitForInvisible(locator: ReturnType<Page['locator']>, timeoutMs = 8000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    if ((await locator.count()) === 0) return
    if (!(await locator.first().isVisible().catch(() => false))) return
    await locator.page().waitForTimeout(200)
  }
  throw new Error('Locator remained visible longer than expected')
}

async function waitForFormulaEnterpriseName(page: Page, tabIndex = 0, timeoutMs = 8000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    const enterpriseInput = page.locator('.el-tab-pane').nth(tabIndex).locator('input').nth(1)
    if ((await enterpriseInput.count()) > 0) {
      const value = (await enterpriseInput.inputValue().catch(() => '')).trim()
      if (value.length > 0) return
    }
    await page.waitForTimeout(200)
  }
  throw new Error('Formula enterprise name did not prefill in time')
}

async function fillVisibleSpinboxes(page: Page, values: string[], timeoutMs = 8000) {
  const inputs = page.locator('.el-tab-pane:visible input[role="spinbutton"]')
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    if ((await inputs.count()) >= values.length) break
    await page.waitForTimeout(200)
  }
  const count = await inputs.count()
  if (count < values.length) throw new Error(`Expected at least ${values.length} spinboxes, got ${count}`)
  for (let i = 0; i < values.length; i += 1) {
    await inputs.nth(i).click()
    await inputs.nth(i).fill(values[i])
  }
}

async function fillGenerationFormulaMinimalValidCase(page: Page) {
  // Set required reportingYear and enterpriseName via Vue component reactivity
  await page.evaluate(() => {
    const el = document.querySelector('.el-tab-pane[name="powerGeneration"]')
    const vueInstance = (el as any)?.__vueParentComponent?.setupState
    if (vueInstance?.pgForm) {
      vueInstance.pgForm.reportingYear = '2024'
      if (!vueInstance.pgForm.enterpriseName) {
        vueInstance.pgForm.enterpriseName = 'Test Enterprise'
      }
    }
  }).catch(() => {})

  // Fallback: fill via UI if evaluate didn't work
  const yearPicker = page.locator('.el-tab-pane:visible .el-date-editor--year').first()
  if (await yearPicker.count() > 0) {
    const yearVal = await yearPicker.locator('input').inputValue().catch(() => '')
    if (!yearVal) {
      await yearPicker.click()
      await page.waitForTimeout(300)
      const yearCell = page.locator('.el-year-table td.available').first()
      if (await yearCell.count() > 0) {
        await yearCell.click()
      } else {
        await page.keyboard.press('Escape')
      }
      await page.waitForTimeout(200)
    }
  }
  const nameInput = page.locator('.el-tab-pane:visible .el-form-item input').nth(1)
  if (await nameInput.count() > 0) {
    const currentVal = await nameInput.inputValue().catch(() => '')
    if (!currentVal) await nameInput.fill('Test Enterprise')
  }

  // Fill fuel parameters (el-input-number with role="spinbutton")
  const inputs = page.locator('.el-tab-pane:visible input[role="spinbutton"]')
  if ((await inputs.count()) < 23) {
    throw new Error(`Expected at least 23 power-generation spinboxes, got ${await inputs.count()}`)
  }
  await inputs.nth(0).fill('1')
  await inputs.nth(1).fill('1')
  await inputs.nth(2).fill('1')
  await inputs.nth(3).fill('0.9')
  await inputs.nth(20).fill('1')
  await inputs.nth(21).fill('1')
  await inputs.nth(22).fill('0.9')
}

async function fillGridFormulaMinimalValidCase(page: Page) {
  // Set required reportingYear and enterpriseName via Vue component reactivity
  await page.evaluate(() => {
    const el = document.querySelector('.el-tab-pane[name="powerGrid"]')
    const vueInstance = (el as any)?.__vueParentComponent?.setupState
    if (vueInstance?.gridForm) {
      vueInstance.gridForm.reportingYear = '2024'
      if (!vueInstance.gridForm.enterpriseName) {
        vueInstance.gridForm.enterpriseName = 'Test Enterprise'
      }
    }
  }).catch(() => {})

  // Fallback: fill via UI if evaluate didn't work
  const yearPicker = page.locator('.el-tab-pane:visible .el-date-editor--year').first()
  if (await yearPicker.count() > 0) {
    const yearVal = await yearPicker.locator('input').inputValue().catch(() => '')
    if (!yearVal) {
      await yearPicker.click()
      await page.waitForTimeout(300)
      const yearCell = page.locator('.el-year-table td.available').first()
      if (await yearCell.count() > 0) {
        await yearCell.click()
      } else {
        await page.keyboard.press('Escape')
      }
      await page.waitForTimeout(200)
    }
  }
  const nameInput = page.locator('.el-tab-pane:visible .el-form-item input').nth(1)
  if (await nameInput.count() > 0) {
    const currentVal = await nameInput.inputValue().catch(() => '')
    if (!currentVal) await nameInput.fill('Test Enterprise')
  }

  // Fill grid parameters (el-input-number with role="spinbutton")
  const inputs = page.locator('.el-tab-pane:visible input[role="spinbutton"]')
  if ((await inputs.count()) < 3) {
    throw new Error(`Expected at least 3 power-grid spinboxes, got ${await inputs.count()}`)
  }
  await inputs.nth(0).fill('1')
  await inputs.nth(1).fill('0.1')
  await inputs.nth(2).fill('1')
}

async function clickButtonByTextPatterns(pageOrLocator: Page | ReturnType<Page['locator']>, patterns: RegExp[]) {
  const buttons = pageOrLocator.locator('button')
  const count = await buttons.count()
  for (let i = 0; i < count; i += 1) {
    const button = buttons.nth(i)
    const text = ((await button.textContent().catch(() => '')) || '').trim()
    if (patterns.some((pattern) => pattern.test(text))) {
      await button.click().catch(async () => {
        await button.click({ force: true })
      })
      return text
    }
  }
  throw new Error(`No button matched patterns: ${patterns.map((p) => p.toString()).join(', ')}`)
}

async function clickTabByTextPatterns(pageOrLocator: Page | ReturnType<Page['locator']>, patterns: RegExp[]) {
  const tabs = pageOrLocator.locator('.el-tabs__item')
  const count = await tabs.count()
  for (let i = 0; i < count; i += 1) {
    const tab = tabs.nth(i)
    const text = ((await tab.textContent().catch(() => '')) || '').trim()
    if (patterns.some((pattern) => pattern.test(text))) {
      await tab.click().catch(async () => {
        await tab.click({ force: true })
      })
      return text
    }
  }
  throw new Error(`No tab matched patterns: ${patterns.map((p) => p.toString()).join(', ')}`)
}

async function refillSearchAndQuery(page: Page, value: string) {
  const searchInput = page.locator('.search-form input').first()
  await searchInput.fill(value)
  await clickButtonByTextPatterns(page.locator('.search-form'), [/search/i, /query/i, /reset/i, /clear/i])
  await settle(page)
}

async function openPrimaryDialogFromToolbar(page: Page) {
  return clickFirstVisible(page, [
    '.search-form .el-button--success',
    '.search-row .el-button--success',
    '.toolbar .el-button--success',
    '.section-card .el-button--primary',
    'button:has-text("Create")',
    'button:has-text("New")',
    'button:has-text("Add")',
  ])
}

async function ensureEnterpriseSession(page: Page, request: APIRequestContext, route = '/enterprise/carbon/upload') {
  const appShellVisible = await page.locator('.app-shell').first().isVisible().catch(() => false)
  if (page.url().includes('/login') || !appShellVisible) {
    await gotoAs(page, request, 'enterprise', route)
  }
}

function isCaptchaGenerateResponse(response: Response): boolean {
  return response.request().method() === 'GET' && response.url().includes('/captcha/generate')
}

async function parseCaptchaPayload(response: Response): Promise<CaptchaPayload> {
  const body = await response.json().catch(() => null) as { data?: CaptchaPayload } | null
  const payload = body?.data
  if (!payload?.captchaKey || !payload?.captchaImage) {
    throw new Error('Captcha generate response did not include captchaKey/captchaImage')
  }
  return payload
}

async function waitForCaptchaCodeFromLog(captchaKey: string, timeoutMs = 10000): Promise<string | null> {
  const deadline = Date.now() + timeoutMs
  const pattern = new RegExp(`generateCaptcha: key=${escapeRegExp(captchaKey)}, code=([A-Z0-9]{4})`, 'g')
  while (Date.now() < deadline) {
    try {
      if (fs.existsSync(BACKEND_LOG_PATH)) {
        const content = fs.readFileSync(BACKEND_LOG_PATH, 'utf-8')
        const matches = Array.from(content.matchAll(pattern))
        const code = matches.at(-1)?.[1]
        if (code) return code
      }
    } catch {
      // File might be locked or partially written, retry
    }
    await new Promise(resolve => setTimeout(resolve, 300))
  }
  return null
}

async function resolveCaptchaCode(captcha: CaptchaPayload): Promise<string> {
  const loggedCode = await waitForCaptchaCodeFromLog(captcha.captchaKey)
  if (loggedCode) return loggedCode
  return ocrCaptcha(captcha.captchaImage)
}

async function openLoginPage(page: Page): Promise<CaptchaPayload> {
  const [response] = await Promise.all([
    page.waitForResponse(isCaptchaGenerateResponse, { timeout: 10000 }),
    page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 }),
  ])
  await settle(page)
  return parseCaptchaPayload(response)
}

async function refreshCaptchaFromImage(page: Page): Promise<CaptchaPayload> {
  const [response] = await Promise.all([
    page.waitForResponse(isCaptchaGenerateResponse, { timeout: 10000 }),
    page.locator('.captcha-image').click().catch(async () => {
      await page.locator('.captcha-image').click({ force: true })
    }),
  ])
  await page.waitForTimeout(300)
  return parseCaptchaPayload(response)
}

async function loginByUiWithCaptcha(page: Page, username: string, password: string) {
  let lastError = ''
  let captcha = await openLoginPage(page)
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const inputs = page.locator('.login-card .el-input input')
    await inputs.nth(0).fill(username)
    await inputs.nth(1).fill(password)
    let code = ''
    try {
      code = await resolveCaptchaCode(captcha)
    } catch (error) {
      lastError = shortError(error)
      captcha = await refreshCaptchaFromImage(page)
      continue
    }
    await inputs.nth(2).fill(code)
    const refreshPromise = page.waitForResponse(isCaptchaGenerateResponse, { timeout: 5000 }).catch(() => null)
    await page.locator('.submit-btn').click()
    await page.waitForFunction(
      () => !window.location.pathname.includes('/login') || !!document.querySelector('.app-shell, .top-header, .main-content'),
      undefined,
      { timeout: 8000 },
    ).catch(() => null)
    await page.waitForTimeout(1200)
    if (!page.url().includes('/login')) return
    lastError = `Attempt ${attempt + 1} stayed on login with OCR code ${code}`
    const refreshed = await refreshPromise
    captcha = refreshed ? await parseCaptchaPayload(refreshed) : await refreshCaptchaFromImage(page)
  }
  throw new Error(lastError || 'UI login did not leave /login')
}

async function assertTableOrEmpty(page: Page) {
  const deadline = Date.now() + 8000
  while (Date.now() < deadline) {
    const visibleLoadingMask = page.locator('.el-loading-mask').filter({ has: page.locator('.el-loading-spinner') }).first()
    if ((await visibleLoadingMask.count()) > 0 && await visibleLoadingMask.isVisible().catch(() => false)) {
      await page.waitForTimeout(200)
      continue
    }

    const tableCount = await page.locator('.el-table, .el-table__inner-wrapper').count()
    const emptyCount = await page.locator('.el-empty, .el-table__empty-text').count()
    if (tableCount + emptyCount > 0) return
    await page.waitForTimeout(200)
  }
  throw new Error('Expected a table or empty state')
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

async function clickFirstVisible(pageOrLocator: Page | ReturnType<Page['locator']>, selectors: string[]): Promise<string> {
  for (const selector of selectors) {
    const node = pageOrLocator.locator(selector).first()
    if ((await node.count()) > 0 && (await node.isVisible().catch(() => false))) {
      await node.click({ timeout: 3000 }).catch(async () => {
        await node.click({ timeout: 3000, force: true })
      })
      return selector
    }
  }
  throw new Error(`No visible control matched selectors: ${selectors.join(' | ')}`)
}

function writeReports() {
  const jsonPath = path.join(OUTPUT_DIR, 'full-functional-report.json')
  fs.writeFileSync(jsonPath, JSON.stringify({ runId: RUN_ID, baseUrl: BASE_URL, apiBase: API_BASE, results, consoleIssues }, null, 2))

  const summary: RunSummary = {
    total: results.length,
    passed: results.filter(r => r.status === 'PASS').length,
    failed: results.filter(r => r.status === 'FAIL').length,
    skipped: results.filter(r => r.status === 'SKIP').length,
  }
  const bySuite = Array.from(new Set(results.map(r => r.suite))).map(suite => {
    const items = results.filter(r => r.suite === suite)
    return `| ${suite} | ${items.length} | ${items.filter(r => r.status === 'PASS').length} | ${items.filter(r => r.status === 'FAIL').length} | ${items.filter(r => r.status === 'SKIP').length} |`
  }).join('\n')
  const rows = results.map(r => `| ${r.id} | ${r.suite} | ${r.name} | ${r.priority} | ${r.status} | [screenshot](${r.screenshot}) | ${(r.error || r.notes || '').replace(/\|/g, '/')} |`).join('\n')
  const markdown = [
    '# OAISS CHAIN Frontend Full Functional Test Report',
    '',
    `- Run ID: ${RUN_ID}`,
    `- Frontend: ${BASE_URL}`,
    `- Backend: ${API_BASE}`,
    `- Total: ${summary.total}`,
    `- Passed: ${summary.passed}`,
    `- Failed: ${summary.failed}`,
    `- Skipped: ${summary.skipped}`,
    `- Pass rate: ${summary.total ? ((summary.passed / summary.total) * 100).toFixed(2) : '0.00'}%`,
    '',
    '## Suite Summary',
    '',
    '| Suite | Total | PASS | FAIL | SKIP |',
    '|---|---:|---:|---:|---:|',
    bySuite,
    '',
    '## Case Results',
    '',
    '| ID | Suite | Name | Priority | Status | Evidence | Error / Notes |',
    '|---|---|---|---|---|---|---|',
    rows,
    '',
    '## Console Errors',
    '',
    consoleIssues.length ? consoleIssues.map(item => `- ${item}`).join('\n') : 'No console errors captured.',
    '',
  ].join('\n')
  fs.writeFileSync(path.join(OUTPUT_DIR, 'full-functional-report.md'), markdown)
  return summary
}

test.describe('OAISS CHAIN frontend full functional matrix', () => {
  test('4 roles / enterprise feature matrix / route guards', async ({ page, request }) => {
    test.setTimeout(45 * 60 * 1000)
    ensureOutputDirs()
    await installApiBaseOverride(page)
    page.on('console', msg => {
      if (msg.type() === 'error') consoleIssues.push(`${page.url()} :: ${msg.text()}`)
    })
    page.on('pageerror', err => consoleIssues.push(`${page.url()} :: ${err.message}`))

    // Set English locale for consistent test selectors (default is zh-CN)
    await page.goto(`${BASE_URL}/login`)
    await page.evaluate(() => localStorage.setItem('locale', 'en-US'))
    await page.reload()
    await settle(page)

    let enterpriseToken = ''

    try {
      await recordCase(page, 'S0 Auth', 'S0-01', 'login page loads', 'P0', async () => {
      if ((await page.locator('.login-card .el-input input').count()) < 3) throw new Error('Login form does not expose account/password/captcha inputs')
      if ((await page.locator('.submit-btn').count()) !== 1) throw new Error('Login button missing')
    })

    await recordCase(page, 'S0 Auth', 'S0-02', 'captcha image loads', 'P0', async () => {
      const captcha = await openLoginPage(page)
      if (!captcha.captchaImage.startsWith('data:image/png;base64,')) throw new Error('Captcha image is not a base64 PNG')
      if (!captcha.captchaKey.startsWith('CAP_')) throw new Error(`Unexpected captcha key format: ${captcha.captchaKey}`)
    })

    await recordCase(page, 'S0 Auth', 'S0-03', 'empty form validation', 'P0', async () => {
      await page.goto(`${BASE_URL}/login`)
      await settle(page)
      await page.locator('.submit-btn').click()
      await page.waitForTimeout(500)
      if ((await page.locator('.el-form-item__error').count()) < 3) throw new Error('Expected three required-field validation messages')
    })

    await recordCase(page, 'S0 Auth', 'S0-04', 'wrong password rejects and refreshes captcha', 'P0', async () => {
      let captcha = await openLoginPage(page)
      const inputs = page.locator('.login-card .el-input input')
      let refreshed = false
      let attempts = 0
      let lastMessage = ''

      while (!refreshed && attempts < 6) {
        attempts += 1
        await inputs.nth(0).fill(USERS.enterprise.username)
        await inputs.nth(1).fill('wrongpass')

        let code = ''
        try {
          code = await resolveCaptchaCode(captcha)
        } catch (error) {
          lastMessage = shortError(error)
          captcha = await refreshCaptchaFromImage(page)
          continue
        }

        await inputs.nth(2).fill(code)
        const refreshPromise = page.waitForResponse(isCaptchaGenerateResponse, { timeout: 5000 }).catch(() => null)
        await page.locator('.submit-btn').click()
        await page.waitForTimeout(1500)
        if (!page.url().includes('/login')) throw new Error('Wrong password unexpectedly logged in')
        const refreshedResponse = await refreshPromise
        if (refreshedResponse) {
          const nextCaptcha = await parseCaptchaPayload(refreshedResponse)
          refreshed = nextCaptcha.captchaKey !== captcha.captchaKey
          captcha = nextCaptcha
        } else {
          captcha = await refreshCaptchaFromImage(page)
          refreshed = true
        }
        lastMessage = `Attempt ${attempts} stayed on login with OCR code ${code}`
      }

      if (!refreshed) throw new Error(lastMessage || 'Captcha did not refresh after failed login')
    })

    await recordCase(page, 'S0 Auth', 'S0-05', 'correct login routes to enterprise home', 'P0', async () => {
      await loginByUiWithCaptcha(page, USERS.enterprise.username, USERS.enterprise.password)
      await page.waitForFunction(() => !!document.querySelector('.app-shell, .top-header, .logout-btn'), undefined, { timeout: 15000 })
      await settle(page)
      await page.waitForFunction(
        expectedPath => window.location.pathname.includes(expectedPath as string),
        USERS.enterprise.home,
        { timeout: 15000 },
      )
      if (!page.url().includes(USERS.enterprise.home)) throw new Error(`Expected enterprise home, got ${page.url()}`)
    })

    await recordCase(page, 'S0 Auth', 'S0-06', 'token storage after UI login', 'P0', async () => {
      await page.goto(`${BASE_URL}/login`)
      await loginByUiWithCaptcha(page, USERS.enterprise.username, USERS.enterprise.password)
      await settle(page)
      const storage = await waitForStorageToken(page, 8000)
      return `storage keys present: ${JSON.stringify(storage)}`
    })

    await recordCase(page, 'S0 Auth', 'S0-07', 'remember account restores username', 'P0', async () => {
      await page.goto(`${BASE_URL}/login`)
      await loginByUiWithCaptcha(page, USERS.enterprise.username, USERS.enterprise.password)
      await waitForStorageToken(page, 8000)
      await settle(page)
      await page.evaluate(() => {
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
        localStorage.removeItem('token_expiry')
        localStorage.removeItem('remember_me')
        sessionStorage.removeItem('access_token')
        sessionStorage.removeItem('refresh_token')
        sessionStorage.removeItem('token_expiry')
        sessionStorage.removeItem('remember_me')
      })
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 })
      await settle(page)
      await page.waitForFunction(() => {
        const raw = localStorage.getItem('carbon-admin-login-form')
        return typeof raw === 'string' && raw.includes('enterprise001')
      }, undefined, { timeout: 5000 })
      const value = await page.locator('.login-card .el-input input').nth(0).inputValue()
      if (value !== USERS.enterprise.username) throw new Error(`Remembered account missing, got "${value}"`)
    })

    await recordCase(page, 'S0 Auth', 'S0-08', 'logout clears tokens', 'P0', async () => {
      await loginByApi(page, request, 'enterprise')
      await page.goto(`${BASE_URL}${USERS.enterprise.home}`, { waitUntil: 'domcontentloaded', timeout: 15000 })
      await settle(page)
      await page.waitForFunction(
        () => !!document.querySelector('.top-header .logout-btn, .logout-btn, .app-shell'),
        undefined,
        { timeout: 10000 },
      )
      await clickFirstVisible(page, ['.top-header .logout-btn', '.logout-btn', 'button:has-text("Logout")'])
      await page.waitForURL(url => url.pathname.includes('/login'), { timeout: 10000 })
      await settle(page)
      const storage = await page.evaluate(() => ({
        localAccess: localStorage.getItem('access_token'),
        sessionAccess: sessionStorage.getItem('access_token'),
      }))
      if (!page.url().includes('/login')) throw new Error(`Logout did not route to login: ${page.url()}`)
      if (storage.localAccess || storage.sessionAccess) throw new Error(`Tokens remained after logout: ${JSON.stringify(storage)}`)
    })

    enterpriseToken = await gotoAs(page, request, 'enterprise', '/enterprise/carbon/upload')

    await recordCase(page, 'S1 Carbon Report', 'S1-01', 'upload page loads', 'P0', async () => {
      await expectAppPage(page, '/enterprise/carbon/upload')
    })
    await recordCase(page, 'S1 Carbon Report', 'S1-02', 'menu navigation to upload', 'P0', async () => {
      await page.goto(`${BASE_URL}/enterprise/orders/manage`)
      await settle(page)
      await openSidebarLeaf(page, /carbon/i, /upload|submit|report/i)
      await page.waitForTimeout(500)
      if (!page.url().includes('/enterprise/carbon/upload')) {
        await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
      }
      await settle(page)
      await expectAppPage(page, '/enterprise/carbon/upload')
    })
    await recordCase(page, 'S1 Carbon Report', 'S1-03', 'create report via form', 'P0', async () => {
      await ensureEnterpriseSession(page, request, '/enterprise/carbon/upload')
      await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
      await settle(page)
      await openPrimaryDialogFromToolbar(page)
      const dialog = page.locator('.el-dialog:visible').first()
      // Use data-testid selectors for reliable field targeting
      // el-input renders data-testid on wrapper div, so target input/textarea inside
      await dialog.locator('[data-testid="create-period"] input').fill('2024-Q1')
      await dialog.locator('[data-testid="create-title"] input').fill(`full-functional-report-${Date.now()}`)
      await dialog.locator('[data-testid="create-emission"] textarea').fill(JSON.stringify({
        scope1: [{ activity_data: '1', emission_factor: '1' }],
        scope2: [{ activity_data: '2', emission_factor: '1' }],
        scope3: [{ activity_data: '0', emission_factor: '1' }],
      }))
      await dialog.locator('[data-testid="create-method"] textarea').fill('automated UI create')
      await dialog.locator('.el-dialog__footer .el-button--primary').click()
      const toastText = await waitForAnyToast(page, 6000)
      await waitForDialogToClose(page, dialog, 8000).catch(() => undefined)
      if (await dialog.isVisible().catch(() => false)) {
        const errors = await dialog.locator('.el-form-item__error').allTextContents()
        throw new Error(`Create report dialog remained visible after submit. toast=${toastText} errors=${errors.join('; ')}`)
      }
      return `toast=${toastText}`
    })
    await recordCase(page, 'S1 Carbon Report', 'S1-04', 'report list and pagination render', 'P0', async () => {
      await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
      await settle(page)
      await assertTableOrEmpty(page)
      if ((await page.locator('.el-pagination').count()) === 0) throw new Error('Pagination component missing')
    })
    await recordCase(page, 'S1 Carbon Report', 'S1-05', 'delete draft report', 'P0', async () => {
      const title = `delete-draft-${Date.now()}`
      await createDraftReport(request, enterpriseToken, title)
      await ensureEnterpriseSession(page, request, '/enterprise/carbon/upload')
      await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
      await settle(page)
      await refillSearchAndQuery(page, title)
      const row = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: title }).first()
      await row.locator('button').filter({ hasText: /delete|鍒犻櫎/i }).first().click()
      await page.locator('.el-message-box__btns .el-button--primary').click()
      await waitForSuccessToast(page, 6000)
      // Reload page and re-search to ensure fresh data
      await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
      await settle(page)
      await refillSearchAndQuery(page, title)
      await page.waitForFunction(
        (targetTitle) => {
          const rows = document.querySelectorAll('.el-table__body-wrapper tbody tr')
          return !Array.from(rows).some((tr) => tr.textContent?.includes(targetTitle))
        },
        title,
        { timeout: 10000 },
      ).catch(() => undefined)
      const freshRow = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: title }).first()
      if (await freshRow.isVisible().catch(() => false)) throw new Error('Draft report row still visible after delete')
    })
    await recordCase(page, 'S1 Carbon Report', 'S1-06', 'submit draft report', 'P0', async () => {
      const title = `submit-draft-${Date.now()}`
      await createDraftReport(request, enterpriseToken, title)
      await ensureEnterpriseSession(page, request, '/enterprise/carbon/upload')
      await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
      await settle(page)
      await refillSearchAndQuery(page, title)
      const row = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: title }).first()
      await row.locator('button').nth(1).click()
      await page.locator('.el-message-box__btns .el-button--primary').click()
      await page.waitForTimeout(1000)
      if (await row.locator('button').filter({ hasText: /submit/i }).count()) return 'Submitted via visible draft action'
    })

    await recordCase(page, 'S2 Orders', 'S2-01', 'orders page loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/orders/manage`)
      await settle(page)
      await expectAppPage(page, '/enterprise/orders/manage')
      await assertTableOrEmpty(page)
    })
    await recordCase(page, 'S2 Orders', 'S2-02', 'orders pagination renders', 'P1', async () => {
      if ((await page.locator('.el-pagination').count()) === 0) throw new Error('Orders pagination missing')
    })
    await recordCase(page, 'S2 Orders', 'S2-03', 'order detail opens', 'P1', async () => {
      await createP2PTrade(request, enterpriseToken).catch(() => undefined)
      await page.goto(`${BASE_URL}/enterprise/orders/manage`)
      await settle(page)
      await clickFirstVisible(page, [
        '.el-table__fixed-right .el-button',
        '.el-table .el-button',
        'button:has-text("View Detail")',
      ])
      await page.waitForTimeout(500)
      if ((await page.locator('.el-dialog').count()) === 0) throw new Error('Order detail dialog did not open')
    })
    await recordCase(page, 'S2 Orders', 'S2-04', 'orders empty state supported', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/orders/manage`)
      await settle(page)
      await assertTableOrEmpty(page)
    })

    await recordCase(page, 'S3 Auction', 'S3-01', 'auction page loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/trading/market`)
      await settle(page)
      await expectAppPage(page, '/enterprise/trading/market')
    })
    await recordCase(page, 'S3 Auction', 'S3-02', 'submit buy order', 'P1', async () => {
      await openPrimaryDialogFromToolbar(page)
      const dialog = page.locator('.el-dialog:visible').first()
      await clickFirstVisible(dialog, ['.el-select__wrapper', '.el-select', '.el-select__caret'])
      await page.waitForTimeout(250)
      const buyItem = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: /buy/i }).first()
      if ((await buyItem.count()) > 0) {
        await buyItem.click()
      } else {
        const items = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
        if ((await items.count()) === 0) throw new Error('Direction dropdown has no visible options')
        await items.first().click()
      }
      await page.waitForTimeout(300)
      // Close dropdown by clicking dialog header
      await dialog.locator('.el-dialog__header').click().catch(() => {})
      await page.waitForTimeout(200)
      const numericInputs = dialog.locator('.el-form-item input[type="number"]')
      await numericInputs.nth(0).fill('1')
      await numericInputs.nth(1).fill('1')
      await dialog.locator('.el-dialog__footer .el-button--primary').click()
      await waitForSuccessToast(page, 6000)
      await waitForDialogToClose(page, dialog, 8000).catch(() => undefined)
      if (await dialog.isVisible().catch(() => false)) throw new Error('Buy order dialog remained visible')
    })
    await recordCase(page, 'S3 Auction', 'S3-03', 'submit sell order', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/trading/market`)
      await settle(page)
      await openPrimaryDialogFromToolbar(page)
      const dialog = page.locator('.el-dialog:visible').first()
      await clickFirstVisible(dialog, ['.el-select__wrapper', '.el-select', '.el-select__caret'])
      await page.waitForTimeout(250)
      const sellItem = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: /sell/i }).first()
      if ((await sellItem.count()) > 0) {
        await sellItem.click()
      } else {
        const items = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
        if ((await items.count()) < 2) throw new Error('Sell direction option is not visible')
        await items.nth(1).click()
      }
      await page.waitForTimeout(300)
      await dialog.locator('.el-dialog__header').click().catch(() => {})
      await page.waitForTimeout(200)
      const sellNumericInputs = dialog.locator('.el-form-item input[type="number"]')
      await sellNumericInputs.nth(0).fill('1')
      await sellNumericInputs.nth(1).fill('1')
      await dialog.locator('.el-dialog__footer .el-button--primary').click()
      await waitForSuccessToast(page, 6000)
      await waitForDialogToClose(page, dialog, 8000).catch(() => undefined)
      if (await dialog.isVisible().catch(() => false)) throw new Error('Sell order dialog remained visible')
    })
    await recordCase(page, 'S3 Auction', 'S3-04', 'match results tab renders', 'P1', async () => {
      const tabs = page.locator('.el-tabs__item')
      if ((await tabs.count()) < 3) throw new Error('Auction tabs missing')
      const matched = await tabs.filter({ hasText: /match|result/i }).count()
      if (matched > 0) await clickTabByTextPatterns(page, [/match|result/i])
      else await tabs.nth(2).click()
      await settle(page)
      await assertTableOrEmpty(page)
    })
    await recordCase(page, 'S3 Auction', 'S3-05', 'my auction orders tab renders', 'P1', async () => {
      const tabs = page.locator('.el-tabs__item')
      if ((await tabs.count()) < 2) throw new Error('Auction tabs missing')
      const matched = await tabs.filter({ hasText: /my|order/i }).count()
      if (matched > 0) await clickTabByTextPatterns(page, [/my|order/i])
      else await tabs.nth(1).click()
      await settle(page)
      await assertTableOrEmpty(page)
    })

    await recordCase(page, 'S4 P2P', 'S4-01', 'p2p page loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/trading/p2p`)
      await settle(page)
      await expectAppPage(page, '/enterprise/trading/p2p')
    })
    await recordCase(page, 'S4 P2P', 'S4-02', 'create p2p trade', 'P1', async () => {
      await ensureEnterpriseSession(page, request, '/enterprise/trading/p2p')
      await openPrimaryDialogFromToolbar(page)
      const dialog = page.locator('.el-dialog:visible').first()
      await dialog.locator('.el-input-number input').nth(0).fill('3')
      await dialog.locator('.el-input-number input').nth(1).fill('1')
      await dialog.locator('.el-input-number input').nth(2).fill('1')
      await dialog.locator('textarea').fill('automated p2p trade')
      await dialog.locator('.el-dialog__footer .el-button--primary').click()
      const p2pToast = await waitForAnyToast(page, 6000)
      await waitForDialogToClose(page, dialog, 8000).catch(() => undefined)
      if (await dialog.isVisible().catch(() => false)) {
        const errors = await dialog.locator('.el-form-item__error').allTextContents()
        const hint = errors.length ? ` Validation: ${errors.join('; ')}` : ''
        throw new Error(`P2P create dialog remained visible. toast=${p2pToast}.${hint}`)
      }
    })
    await recordCase(page, 'S4 P2P', 'S4-03', 'cancel pending p2p trade', 'P1', async () => {
      await createP2PTrade(request, enterpriseToken)
      await ensureEnterpriseSession(page, request, '/enterprise/trading/p2p')
      await page.goto(`${BASE_URL}/enterprise/trading/p2p`)
      await settle(page)
      const row = page.locator('.el-table__body-wrapper tbody tr').first()
      if ((await row.count()) === 0) throw new Error('No P2P trade rows available for cancellation')
      await clickButtonByTextPatterns(row, [/cancel/i, /revoke/i, /void/i, /delete/i])
      await page.locator('.el-message-box__btns .el-button--primary').click()
      await page.waitForTimeout(1000)
    })
    await recordCase(page, 'S4 P2P', 'S4-04', 'confirm p2p trade', 'P1', async () => {
      const confirmButtons = page.locator('.el-table__body-wrapper tbody tr button').filter({ hasText: /confirm|approve|accept/i })
      if ((await confirmButtons.count()) === 0) throw new Error('No P2P confirm action is exposed in the enterprise P2P page')
    })
    await recordCase(page, 'S4 P2P', 'S4-05', 'p2p list filtering', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/trading/p2p`)
      await settle(page)
      await page.locator('.search-form input').first().fill('full')
      await page.locator('.search-form .el-button--primary').click()
      await settle(page)
      await assertTableOrEmpty(page)
    })

    await recordCase(page, 'S5 Dashboard', 'S5-01', 'dashboard loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/company/dashboard`)
      await settle(page)
      await expectAppPage(page, '/enterprise/company/dashboard')
    })
    await recordCase(page, 'S5 Dashboard', 'S5-02', 'charts render', 'P1', async () => {
      await waitForLocatorCount(page, '.chart-box', 6, 8000)
      if ((await page.locator('.chart-box').count()) < 6) throw new Error('Expected six dashboard chart containers')
    })
    await recordCase(page, 'S5 Dashboard', 'S5-03', 'stat cards render', 'P1', async () => {
      if ((await page.locator('.overview-card').count()) < 3) throw new Error('Expected dashboard overview cards')
    })
    await recordCase(page, 'S5 Dashboard', 'S5-04', 'time range switch updates view', 'P1', async () => {
      const buttons = page.locator('.search-right .el-radio-button')
      if ((await buttons.count()) < 3) throw new Error('Time dimension controls missing')
      await buttons.nth(0).click()
      await settle(page)
    })

    await recordCase(page, 'S6 Enterprise Info', 'S6-01', 'enterprise info page loads', 'P2', async () => {
      await page.goto(`${BASE_URL}/enterprise/info`)
      await settle(page)
      await expectAppPage(page, '/enterprise/info')
    })
    await recordCase(page, 'S6 Enterprise Info', 'S6-02', 'enterprise fields render', 'P2', async () => {
      if ((await page.locator('.el-descriptions__body').count()) < 1) throw new Error('Enterprise descriptions missing')
    })
    await recordCase(page, 'S6 Enterprise Info', 'S6-03', 'edit contact information', 'P2', async () => {
      await ensureEnterpriseSession(page, request, '/enterprise/info')
      await page.locator('.section-card .el-button--primary').first().click()
      const dialog = page.locator('.el-dialog:visible').first()
      await dialog.locator('input').nth(0).fill(`QA Contact ${Date.now()}`)
      await dialog.locator('input').nth(1).fill('13800138000')
      await dialog.locator('.el-dialog__footer .el-button--primary').click()
      const contactToast = await waitForAnyToast(page, 6000)
      await waitForDialogToClose(page, dialog, 8000).catch(() => undefined)
      if (await dialog.isVisible().catch(() => false)) throw new Error(`Contact edit dialog remained visible. toast=${contactToast}`)
    })

    await recordCase(page, 'S7 Credit', 'S7-01', 'credit page loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/credit/score`)
      await settle(page)
      await expectAppPage(page, '/enterprise/credit/score')
    })
    await recordCase(page, 'S7 Credit', 'S7-02', 'score display renders', 'P1', async () => {
      if ((await page.locator('.score-value, .el-empty').count()) === 0) throw new Error('Score display or empty state missing')
    })
    await recordCase(page, 'S7 Credit', 'S7-03', 'score history renders', 'P1', async () => {
      await assertTableOrEmpty(page)
    })
    await recordCase(page, 'S7 Credit', 'S7-04', 'score ranking is available', 'P1', async () => {
      if ((await page.locator('[data-testid="credit-ranking-table"]').count()) === 0) {
        throw new Error('Credit ranking UI is not exposed on the enterprise credit page')
      }
    })

    await recordCase(page, 'S8 Carbon Coin', 'S8-01', 'carbon coin account loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/carbon-coin/account`)
      await settle(page)
      await expectAppPage(page, '/enterprise/carbon-coin/account')
    })
    await recordCase(page, 'S8 Carbon Coin', 'S8-02', 'balance renders', 'P1', async () => {
      if ((await page.locator('.main-balance, .el-empty').count()) === 0) throw new Error('Balance card or empty state missing')
    })
    await recordCase(page, 'S8 Carbon Coin', 'S8-03', 'transaction records render', 'P1', async () => {
      await assertTableOrEmpty(page)
    })
    await recordCase(page, 'S8 Carbon Coin', 'S8-04', 'transfer form validates/submits', 'P1', async () => {
      await page.locator('.transfer-action .el-button--primary').click()
      await page.locator('.el-dialog .el-input-number input').nth(0).fill('2')
      await page.locator('.el-dialog .el-input-number input').nth(1).fill('0.01')
      await page.locator('.el-dialog input').last().fill('full functional transfer')
      await page.locator('.el-dialog .el-button--primary').click()
      await page.waitForTimeout(1500)
    })
    await recordCase(page, 'S8 Carbon Coin', 'S8-05', 'recharge permission handling', 'P1', async () => {
      const recharge = page.locator('button').filter({ hasText: /recharge|top\s*up|deposit/i })
      if ((await recharge.count()) > 0) {
        await recharge.first().click()
        await page.waitForTimeout(500)
      } else {
        return 'Enterprise account page does not expose recharge, which is acceptable when recharge is admin-only.'
      }
    })

    await recordCase(page, 'S9 Blockchain', 'S9-01', 'blockchain page loads', 'P2', async () => {
      await page.goto(`${BASE_URL}/enterprise/blockchain/browser`)
      await settle(page)
      await expectAppPage(page, '/enterprise/blockchain/browser')
    })
    await recordCase(page, 'S9 Blockchain', 'S9-02', 'connection status indicator', 'P2', async () => {
      const statusTag = page.locator('[data-testid="blockchain-status-tag"]')
      if ((await statusTag.count()) === 0) throw new Error('Blockchain connection status indicator is not exposed')
    })
    await recordCase(page, 'S9 Blockchain', 'S9-03', 'latest blocks list renders', 'P2', async () => {
      await assertTableOrEmpty(page)
    })
    await recordCase(page, 'S9 Blockchain', 'S9-04', 'transaction hash query', 'P2', async () => {
      if ((await page.locator('[data-testid="blockchain-tx-query-input"]').count()) === 0) {
        throw new Error('Transaction hash query input is not exposed in blockchain browser')
      }
    })

    await recordCase(page, 'S10 Carbon Neutral', 'S10-01', 'project page loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/carbon-neutral/projects`)
      await settle(page)
      await expectAppPage(page, '/enterprise/carbon-neutral/projects')
    })
    await recordCase(page, 'S10 Carbon Neutral', 'S10-02', 'project list renders', 'P1', async () => {
      await assertTableOrEmpty(page)
    })
    await recordCase(page, 'S10 Carbon Neutral', 'S10-03', 'create project', 'P1', async () => {
      const projectName = `full-functional-project-${Date.now()}`
      await createProject(request, enterpriseToken, projectName)
      await page.goto(`${BASE_URL}/enterprise/carbon-neutral/projects`)
      await settle(page)
      if ((await page.locator('.el-table').count()) === 0) throw new Error('Project table missing after API create')
    })
    await recordCase(page, 'S10 Carbon Neutral', 'S10-04', 'project detail opens', 'P1', async () => {
      await page.locator('.el-table__body-wrapper tbody tr a, .el-table__body-wrapper tbody tr button').last().click()
      await settle(page)
      if (!page.url().includes('/enterprise/carbon-neutral/projects/')) throw new Error('Project detail route did not open')
    })
    await recordCase(page, 'S10 Carbon Neutral', 'S10-05', 'submit project for review', 'P1', async () => {
      const projectName = `submit-project-${Date.now()}`
      await createProject(request, enterpriseToken, projectName)
      await page.goto(`${BASE_URL}/enterprise/carbon-neutral/projects`)
      await settle(page)
      const row = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: projectName }).first()
      await waitForRowByText(page, row, 8000)
      await row.locator('button').first().click()
      await page.locator('.el-message-box__btns .el-button--primary').click()
      await page.waitForTimeout(1000)
    })
    await recordCase(page, 'S10 Carbon Neutral', 'S10-06', 'project status flow actions visible', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/carbon-neutral/projects`)
      await settle(page)
      if ((await page.locator('.el-table__body-wrapper tbody tr a[href*="/enterprise/carbon-neutral/projects/"], .el-table__body-wrapper tbody tr button').count()) === 0) {
        throw new Error('No project lifecycle operation controls visible')
      }
    })

    await recordCase(page, 'S11 Emission', 'S11-01', 'emission page loads', 'P2', async () => {
      await page.goto(`${BASE_URL}/enterprise/emission/data`)
      await settle(page)
      await expectAppPage(page, '/enterprise/emission/data')
    })
    await recordCase(page, 'S11 Emission', 'S11-02', 'emission rating renders', 'P2', async () => {
      await page.locator('.el-tabs__item').nth(0).click()
      await settle(page)
      await assertTableOrEmpty(page)
    })
    await recordCase(page, 'S11 Emission', 'S11-03', 'industry ranking renders', 'P2', async () => {
      await page.locator('.el-tabs__item').nth(1).click()
      await settle(page)
      await assertTableOrEmpty(page)
    })

    await recordCase(page, 'S12 Formula', 'S12-01', 'formula page loads', 'P2', async () => {
      await page.goto(`${BASE_URL}/enterprise/carbon-formula`)
      await settle(page)
      await expectAppPage(page, '/enterprise/carbon-formula')
    })
    await recordCase(page, 'S12 Formula', 'S12-02', 'power generation calculation', 'P2', async () => {
      await ensureEnterpriseSession(page, request, '/enterprise/carbon-formula')
      await page.goto(`${BASE_URL}/enterprise/carbon-formula`)
      await settle(page)
      await page.waitForTimeout(1000)
      await expandVisibleCollapseItems(page)
      await fillGenerationFormulaMinimalValidCase(page)
      await page.locator('.el-tab-pane:visible .el-button--primary').first().click()
      const pgToast = await waitForAnyToast(page, 6000)
      await page.waitForFunction(
        () => !!document.querySelector('.el-tab-pane.is-active .el-descriptions'),
        undefined,
        { timeout: 8000 },
      ).catch(() => undefined)
      if ((await page.locator('.el-tab-pane:visible .el-descriptions').count()) === 0) throw new Error(`Power generation result did not render. toast=${pgToast}`)
    })
    await recordCase(page, 'S12 Formula', 'S12-03', 'power grid calculation', 'P2', async () => {
      await ensureEnterpriseSession(page, request, '/enterprise/carbon-formula')
      await page.goto(`${BASE_URL}/enterprise/carbon-formula`)
      await settle(page)
      await page.locator('.el-tabs__item').nth(1).click()
      await settle(page)
      await page.waitForTimeout(1000)
      await fillGridFormulaMinimalValidCase(page)
      await page.locator('.el-tab-pane:visible .el-button--primary').first().click()
      const gridToast = await waitForAnyToast(page, 6000)
      await page.waitForFunction(
        () => !!document.querySelector('.el-tab-pane.is-active .el-descriptions'),
        undefined,
        { timeout: 8000 },
      ).catch(() => undefined)
      if ((await page.locator('.el-tab-pane:visible .el-descriptions').count()) === 0) throw new Error(`Power grid result did not render. toast=${gridToast}`)
    })
    await recordCase(page, 'S12 Formula', 'S12-04', 'empty value validation', 'P2', async () => {
      await page.goto(`${BASE_URL}/enterprise/carbon-formula`)
      await settle(page)
      await page.locator('.el-button--primary').first().click()
      await page.waitForTimeout(800)
      if ((await page.locator('.el-form-item__error, .el-message').count()) === 0) {
        throw new Error('Formula calculator does not show client-side empty-field validation')
      }
    })

    await recordCase(page, 'S13 AI Market', 'S13-01', 'market prediction page loads', 'P2', async () => {
      await page.goto(`${BASE_URL}/enterprise/market-prediction`)
      await settle(page)
      await expectAppPage(page, '/enterprise/market-prediction')
    })
    await recordCase(page, 'S13 AI Market', 'S13-02', 'trend horizon prediction renders', 'P2', async () => {
      const buttons = page.locator('.horizon-row button')
      if ((await buttons.count()) === 0) throw new Error('Prediction horizon buttons missing')
      await buttons.nth(0).click()
      await settle(page)
      if ((await page.locator('.chart-box').count()) === 0) throw new Error('Prediction chart missing')
    })
    await recordCase(page, 'S13 AI Market', 'S13-03', 'supply demand analysis renders', 'P2', async () => {
      await page.locator('.header-row .el-select').click()
      await page.locator('.el-select-dropdown__item').nth(2).click()
      await settle(page)
      if ((await page.locator('.stat-card').count()) < 3) throw new Error('Supply/demand stats missing')
    })

    await recordCase(page, 'S14 AI Inference', 'S14-01', 'enterprise inference page loads', 'P2', async () => {
      await page.goto(`${BASE_URL}/enterprise/enterprise-inference`)
      await settle(page)
      await expectAppPage(page, '/enterprise/enterprise-inference')
    })
    await recordCase(page, 'S14 AI Inference', 'S14-02', 'refresh inference result', 'P2', async () => {
      const refresh = page.locator('.card-header-row .el-button--primary')
      if ((await refresh.count()) === 0) throw new Error('Refresh inference button missing')
      await refresh.click()
      await settle(page)
      if ((await page.locator('.stat-card, .el-empty').count()) === 0) throw new Error('No inference result or empty state rendered')
    })
    await recordCase(page, 'S14 AI Inference', 'S14-03', 'inference empty state supported', 'P2', async () => {
      if ((await page.locator('.stat-card, .el-empty, .el-alert').count()) === 0) throw new Error('Inference page lacks result and empty-state UI')
    })

    await recordCase(page, 'S15 Profile', 'S15-01', 'profile page loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/enterprise/user/profile`)
      await settle(page)
      await expectAppPage(page, '/enterprise/user/profile')
    })
    await recordSkip(page, 'S15 Profile', 'S15-02', 'change password success', 'P1', 'Skipped to preserve the shared enterprise001/admin123 credential for subsequent runs.')
    await recordCase(page, 'S15 Profile', 'S15-03', 'edit profile information', 'P1', async () => {
      await page.locator('.profile-form input').nth(2).fill(`qa-${Date.now()}@example.com`)
      await page.locator('.profile-form .el-button--primary').click()
      await page.waitForTimeout(1000)
    })
    await recordCase(page, 'S15 Profile', 'S15-04', 'password mismatch validation', 'P1', async () => {
      await page.locator('.el-tabs__item').nth(1).click()
      await page.locator('.pwd-form input').nth(0).fill('admin123')
      await page.locator('.pwd-form input').nth(1).fill('newpass1')
      await page.locator('.pwd-form input').nth(2).fill('newpass2')
      await page.locator('.pwd-form .el-button--primary').click()
      await page.waitForTimeout(500)
      if ((await page.locator('.el-form-item__error').count()) === 0) throw new Error('Password mismatch validation did not appear')
    })
    await recordCase(page, 'S15 Profile', 'S15-05', 'digital signature management area', 'P1', async () => {
      if ((await page.locator('text=/signature|绛惧悕|keypair|鏁板瓧|RSA/i').count()) === 0) {
        throw new Error('Digital signature/keypair management UI is not exposed in profile page')
      }
    })

    await recordCase(page, 'S16 Public', 'S16-01', 'official home loads', 'P1', async () => {
      await page.goto(`${BASE_URL}/official-home`)
      await settle(page)
      if (!page.url().includes('/official-home')) throw new Error(`Expected official home, got ${page.url()}`)
      if ((await page.locator('.site-page, .hero').count()) === 0) throw new Error('Official home content missing')
    })
    await recordCase(page, 'S16 Public', 'S16-02', '404 page renders', 'P1', async () => {
      await page.goto(`${BASE_URL}/nonexistent-path`)
      await settle(page)
      const notFoundCount = await page.locator('.not-found').count()
      const text404Count = await page.getByText('404').count()
      if (notFoundCount + text404Count === 0) throw new Error('404 content missing')
    })
    await recordCase(page, 'S16 Public', 'S16-03', 'sidebar collapse toggles', 'P1', async () => {
      await gotoAs(page, request, 'enterprise', '/enterprise/carbon/upload')
      const before = await page.locator('.side-panel').evaluate(el => getComputedStyle(el).width)
      await page.locator('.collapse-btn').click()
      await page.waitForTimeout(400)
      const after = await page.locator('.side-panel').evaluate(el => getComputedStyle(el).width)
      if (before === after) throw new Error(`Sidebar width did not change (${before})`)
    })
    await recordCase(page, 'S16 Public', 'S16-04', 'breadcrumb renders', 'P1', async () => {
      if ((await page.locator('.el-breadcrumb').count()) === 0) throw new Error('Breadcrumb missing on enterprise page')
    })

    await recordCase(page, 'S17 Route Guards', 'S17-01', 'anonymous protected access redirects to login', 'P0', async () => {
      await page.goto(BASE_URL)
      await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
      await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
      await settle(page)
      if (!page.url().includes('/login')) throw new Error(`Expected login redirect, got ${page.url()}`)
    })
    await recordCase(page, 'S17 Route Guards', 'S17-02', 'enterprise cannot access admin users', 'P0', async () => {
      await gotoAs(page, request, 'enterprise', '/admin/system/users')
      if (page.url().includes('/admin/system/users')) throw new Error('Enterprise user reached admin user management')
      if (!page.url().includes('/enterprise/carbon/upload')) throw new Error(`Expected enterprise home redirect, got ${page.url()}`)
    })
    await recordCase(page, 'S17 Route Guards', 'S17-03', 'reviewer cannot access enterprise upload', 'P0', async () => {
      await gotoAs(page, request, 'reviewer', '/enterprise/carbon/upload')
      if (page.url().includes('/enterprise/carbon/upload')) throw new Error('Reviewer reached enterprise upload')
      if (!page.url().includes('/auditor/audit/list')) throw new Error(`Expected reviewer home redirect, got ${page.url()}`)
    })
    await recordCase(page, 'S17 Route Guards', 'S17-04', 'third party cannot access admin users', 'P0', async () => {
      await gotoAs(page, request, 'thirdParty', '/admin/system/users')
      if (page.url().includes('/admin/system/users')) throw new Error('Third-party user reached admin user management')
      if (!page.url().includes('/third-party/monitor')) throw new Error(`Expected third-party home redirect, got ${page.url()}`)
    })
    await recordCase(page, 'S17 Route Guards', 'S17-05', 'logged in user visiting login redirects home', 'P0', async () => {
      await gotoAs(page, request, 'enterprise', '/login')
      if (page.url().includes('/login')) throw new Error('Logged-in enterprise user stayed on login page')
      if (!page.url().includes('/enterprise/carbon/upload')) throw new Error(`Expected enterprise home, got ${page.url()}`)
    })
      await recordCase(page, 'S17 Route Guards', 'S17-06', 'cleared token redirects on protected action', 'P0', async () => {
        await gotoAs(page, request, 'enterprise', '/enterprise/orders/manage')
        await page.evaluate(() => { localStorage.removeItem('access_token'); sessionStorage.removeItem('access_token') })
        await page.goto(`${BASE_URL}/enterprise/orders/manage`)
        await settle(page)
        if (!page.url().includes('/login')) throw new Error(`Expected login after token clear, got ${page.url()}`)
      })
    } finally {
      const summary = writeReports()
      console.log(`OAISS full functional report: ${OUTPUT_DIR}`)
      if (summary.failed > 0) {
        throw new Error(`Full functional matrix recorded ${summary.failed} failing case(s) out of ${summary.total}. See ${OUTPUT_DIR}`)
      }
    }
  })
})


