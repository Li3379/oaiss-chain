import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

/**
 * Regression Flow: Verify v1.0 functionality is intact after v1.1 changes.
 *
 * These tests re-exercise critical v1.0 user flows to confirm no regressions
 * were introduced by v1.1 feature additions (AI prediction, certificates,
 * carbon formulas, enterprise admission, reviewer qualification).
 */
test.describe('Flow: v1.0 Regression Tests', () => {
  let adminToken: string
  let enterpriseToken: string
  let reviewerToken: string

  test.beforeAll(async ({ request }) => {
    const adminLogin = await request.post(`${API_BASE}/auth/login`, {
      data: { username: TEST_USERS.admin.username, password: TEST_USERS.admin.password },
    })
    const adminBody = await adminLogin.json()
    adminToken = adminBody.data.accessToken

    const entLogin = await request.post(`${API_BASE}/auth/login`, {
      data: { username: TEST_USERS.enterprise.username, password: TEST_USERS.enterprise.password },
    })
    const entBody = await entLogin.json()
    enterpriseToken = entBody.data.accessToken

    const revLogin = await request.post(`${API_BASE}/auth/login`, {
      data: { username: TEST_USERS.reviewer.username, password: TEST_USERS.reviewer.password },
    })
    const revBody = await revLogin.json()
    reviewerToken = revBody.data.accessToken
  })

  // ─── Auth Flow Regression ────────────────────────────────────────────────

  test.describe('Auth Flow Regression', () => {
    test('login still works', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/enterprise\/carbon\/upload/)
    })

    test('logout still works', async ({ request }) => {
      const response = await request.post(`${API_BASE}/auth/logout`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
      })

      expect(response.status()).toBeLessThan(500)
    })

    test('token refresh endpoint still exists', async ({ request }) => {
      const response = await request.post(`${API_BASE}/auth/refresh`, {
        data: { refreshToken: 'dummy' },
      })

      // Should not return 404 (endpoint exists)
      expect(response.status()).not.toBe(404)
    })

    test('wrong password is rejected', async ({ request }) => {
      const response = await request.post(`${API_BASE}/auth/login`, {
        data: { username: TEST_USERS.enterprise.username, password: 'wrong-password' },
      })

      expect(response.status()).toBeGreaterThanOrEqual(400)
    })
  })

  // ─── Carbon Report Lifecycle Regression ──────────────────────────────────

  test.describe('Carbon Report Lifecycle Regression', () => {
    test('can list carbon reports', async ({ request }) => {
      const response = await request.get(`${API_BASE}/carbon/reports`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
    })

    test('can create carbon report', async ({ request }) => {
      const response = await request.post(`${API_BASE}/carbon/reports`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
        data: {
          accountingPeriod: '2026-Q3-REG',
          title: 'Regression Test Report',
          reportType: 1,
          emissionData: JSON.stringify({ scope1: 100, scope2: 200, scope3: 300 }),
          totalEmission: 600,
        },
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
    })

    test('reviewer can list pending reports', async ({ request }) => {
      const response = await request.get(`${API_BASE}/carbon/reports`, {
        headers: { Authorization: `Bearer ${reviewerToken}` },
        params: { status: 0 },
      })

      expect(response.ok()).toBeTruthy()
    })
  })

  // ─── Carbon Coin Operations Regression ───────────────────────────────────

  test.describe('Carbon Coin Operations Regression', () => {
    test('can get carbon coin account', async ({ request }) => {
      const response = await request.get(`${API_BASE}/carbon-coin/accounts`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
    })

    test('transfer endpoint exists', async ({ request }) => {
      const response = await request.post(`${API_BASE}/carbon-coin/transfer`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
        data: { toUserId: 2, amount: 100 },
      })

      // May fail validation but should not be 404
      expect(response.status()).not.toBe(404)
    })
  })

  // ─── Trading Operations Regression ───────────────────────────────────────

  test.describe('Trading Operations Regression', () => {
    test('can list auction orders', async ({ request }) => {
      const response = await request.get(`${API_BASE}/auction/orders`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
      })

      expect(response.ok()).toBeTruthy()
    })

    test('P2P trade endpoint exists', async ({ request }) => {
      const response = await request.post(`${API_BASE}/trades/p2p`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
        data: { toUserId: 2, quantity: 100, unitPrice: 50 },
      })

      // May fail validation but should not be 404
      expect(response.status()).not.toBe(404)
    })
  })

  // ─── Credit Score Regression ─────────────────────────────────────────────

  test.describe('Credit Score Regression', () => {
    test('can get credit score', async ({ request }) => {
      const response = await request.get(`${API_BASE}/credit-score/1`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
      })

      expect(response.ok()).toBeTruthy()
    })
  })

  // ─── Carbon Neutral Projects Regression ──────────────────────────────────

  test.describe('Carbon Neutral Projects Regression', () => {
    test('can list carbon neutral projects', async ({ request }) => {
      const response = await request.get(`${API_BASE}/carbon-neutral/projects`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
      })

      expect(response.ok()).toBeTruthy()
    })
  })

  // ─── Admin User Management Regression ────────────────────────────────────

  test.describe('Admin User Management Regression', () => {
    test('can list users', async ({ request }) => {
      const response = await request.get(`${API_BASE}/admin/users`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      })

      expect(response.ok()).toBeTruthy()
    })
  })

  // ─── Third-Party Monitoring Regression ───────────────────────────────────

  test.describe('Third-Party Monitoring Regression', () => {
    test('can access monitor page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/third-party/monitor')
      await page.waitForLoadState('networkidle')

      await expect(page).not.toHaveURL(/error|500/)
    })
  })

  // ─── UI Navigation Regression ────────────────────────────────────────────

  test.describe('UI Navigation Regression', () => {
    test('enterprise can navigate to all pages', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)

      const pages = [
        '/enterprise/carbon/upload',
        '/enterprise/carbon-neutral',
        '/enterprise/dashboard',
        '/enterprise/trading/market',
        '/enterprise/trading/p2p',
      ]

      for (const p of pages) {
        await page.goto(p)
        await page.waitForLoadState('networkidle')
        await expect(page).not.toHaveURL(/error|500/)
      }
    })

    test('reviewer can navigate to audit pages', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)

      const pages = [
        '/auditor/audit/list',
        '/auditor/statistics',
      ]

      for (const p of pages) {
        await page.goto(p)
        await page.waitForLoadState('networkidle')
        await expect(page).not.toHaveURL(/error|500/)
      }
    })

    test('admin can navigate to admin pages', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.admin.username, TEST_USERS.admin.password)

      const pages = [
        '/admin/system/users',
        '/admin/verify/list',
      ]

      for (const p of pages) {
        await page.goto(p)
        await page.waitForLoadState('networkidle')
        await expect(page).not.toHaveURL(/error|500/)
      }
    })
  })
})
