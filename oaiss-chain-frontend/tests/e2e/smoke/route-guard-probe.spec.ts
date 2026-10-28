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

test('probe route guards and role isolation in real backend mode', async ({ page }) => {
  test.setTimeout(120_000)
  ensureOutDir()

  // S17-01: unauthenticated protected route
  await page.goto(`${BASE_URL}/login`)
  await settle(page)
  await page.evaluate(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
  await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
  await settle(page)
  const unauthProtectedRedirect = page.url()

  // Enterprise role isolation
  await loginViaApi(page, 'enterprise001', 'admin123')
  await page.goto(`${BASE_URL}/admin/system/users`)
  await settle(page)
  const enterpriseToAdminResult = page.url()

  // Reviewer role isolation
  await page.goto(`${BASE_URL}/login`)
  await settle(page)
  await page.evaluate(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
  await loginViaApi(page, 'reviewer001', 'admin123')
  await page.goto(`${BASE_URL}/enterprise/carbon/upload`)
  await settle(page)
  const reviewerToEnterpriseResult = page.url()

  // Third-party role isolation
  await page.goto(`${BASE_URL}/login`)
  await settle(page)
  await page.evaluate(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
  await loginViaApi(page, 'thirdparty001', 'admin123')
  await page.goto(`${BASE_URL}/admin/system/users`)
  await settle(page)
  const thirdPartyToAdminResult = page.url()

  // S17-05: logged in user visits login
  await page.goto(`${BASE_URL}/login`)
  await settle(page)
  const loggedInVisitLoginResult = page.url()

  // S17-06: token cleared while operating
  await page.goto(`${BASE_URL}/third-party/monitor`)
  await settle(page)
  await page.evaluate(() => {
    localStorage.removeItem('access_token')
    sessionStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    sessionStorage.removeItem('refresh_token')
  })
  await page.goto(`${BASE_URL}/third-party/monitor`)
  await settle(page)
  const tokenClearedResult = page.url()

  await page.screenshot({ path: path.join(OUT_DIR, 'route-guard-probe-2026-05-24.png'), fullPage: true })
  writeJson('route-guard-probe-2026-05-24.json', {
    unauthProtectedRedirect,
    enterpriseToAdminResult,
    reviewerToEnterpriseResult,
    thirdPartyToAdminResult,
    loggedInVisitLoginResult,
    tokenClearedResult,
  })
})

