import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'
import { isMlServiceAvailable } from '../fixtures/test-env'
import { MarketPredictionPage } from '../fixtures/page-objects/MarketPredictionPage'
import { EnterpriseInferencePage } from '../fixtures/page-objects/EnterpriseInferencePage'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

test.describe('AI Prediction Flow', () => {
  // Skip all tests if ML service is unavailable
  test.beforeAll(async () => {
    const mlAvailable = await isMlServiceAvailable()
    if (!mlAvailable) {
      console.warn('ML service not available, skipping AI prediction tests')
    }
  })

  // ─── REQ-01: Market Prediction API ────────────────────────────────────────

  test.describe('Market Prediction API (REQ-01)', () => {
    test('should return market trend forecast', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/ai/market/trend`, {
        params: { horizonDays: 30 },
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toBeDefined()
      // MarketForecastResponse has forecastDates, forecastPrices, lowerBound, upperBound, trend
      expect(body.data).toHaveProperty('forecastDates')
      expect(body.data).toHaveProperty('forecastPrices')
      expect(body.data).toHaveProperty('trend')
    })

    test('should return carbon price forecast', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/ai/market/price`, {
        params: { horizonDays: 30 },
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toBeDefined()
      expect(body.data).toHaveProperty('forecastPrices')
      if (body.data.forecastPrices) {
        expect(body.data.forecastPrices.length).toBeGreaterThan(0)
      }
    })

    test('should return supply/demand forecast', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/ai/market/supply-demand`, {
        params: { horizonDays: 30 },
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toBeDefined()
      expect(body.data).toHaveProperty('forecastDates')
    })

    test('should reject horizonDays exceeding maximum (365)', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/ai/market/trend`, {
        params: { horizonDays: 500 },
      })

      expect(response.status()).toBeGreaterThanOrEqual(400)
    })
  })

  // ─── REQ-02: Enterprise Inference API ─────────────────────────────────────

  test.describe('Enterprise Inference API (REQ-02)', () => {
    test('should return enterprise inference results', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.get(`${API_BASE}/predict/enterprise/1/inference`)

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toBeDefined()
      // EnterpriseInferenceResponse fields
      expect(body.data).toHaveProperty('enterpriseId')
      expect(body.data).toHaveProperty('complianceStatus')
      expect(body.data).toHaveProperty('confidence')
      expect(body.data).toHaveProperty('anomalyScore')
      expect(body.data).toHaveProperty('isAnomaly')
    })

    test('should include compliance status as valid value', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.get(`${API_BASE}/predict/enterprise/1/inference`)
      const body = await response.json()

      expect(body.data).toHaveProperty('complianceStatus')
      expect(['compliant', 'warning', 'non-compliant']).toContain(body.data.complianceStatus)
    })

    test('should return 404 for non-existent enterprise', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.get(`${API_BASE}/predict/enterprise/99999/inference`)

      expect(response.status()).toBe(404)
    })
  })

  // ─── REQ-04: Market Prediction Frontend ───────────────────────────────────

  test.describe('Market Prediction Frontend (REQ-04)', () => {
    test('enterprise user can access market prediction page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      const marketPage = new MarketPredictionPage(page)

      await marketPage.goto()
      await marketPage.expectLoaded()
    })

    test('market prediction page displays chart', async ({ page }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      const marketPage = new MarketPredictionPage(page)

      await marketPage.goto()
      await marketPage.expectChartVisible()
    })

    test('market prediction page displays stats row', async ({ page }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      const marketPage = new MarketPredictionPage(page)

      await marketPage.goto()
      await marketPage.expectStatsRow()

      const count = await marketPage.getStatCardCount()
      expect(count).toBeGreaterThanOrEqual(3)
    })

    test('user can interact with horizon selector', async ({ page }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      const marketPage = new MarketPredictionPage(page)

      await marketPage.goto()
      await marketPage.expectHorizonSelector()
      await marketPage.selectHorizon(90)

      // Wait for API call and chart re-render
      await page.waitForTimeout(2000)
      await marketPage.expectChartVisible()
    })
  })

  // ─── REQ-04: Enterprise Inference Frontend ────────────────────────────────

  test.describe('Enterprise Inference Frontend (REQ-04)', () => {
    test('enterprise user can access inference page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      const inferencePage = new EnterpriseInferencePage(page)

      await inferencePage.goto()
      await inferencePage.expectLoaded()
    })

    test('inference page displays compliance status', async ({ page }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      const inferencePage = new EnterpriseInferencePage(page)

      await inferencePage.goto()
      await inferencePage.expectComplianceStatus()
    })

    test('inference page displays stat cards', async ({ page }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      const inferencePage = new EnterpriseInferencePage(page)

      await inferencePage.goto()
      await inferencePage.expectStatCards()
    })

    test('reviewer can access enterprise inference', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      const inferencePage = new EnterpriseInferencePage(page)

      await inferencePage.goto()
      await inferencePage.expectLoaded()
    })

    test('third-party can access enterprise inference', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      const inferencePage = new EnterpriseInferencePage(page)

      await inferencePage.goto()
      await inferencePage.expectLoaded()
    })
  })

  // ─── REQ-03: Carbon Emission Prediction ──────────────────────────────────

  test.describe('Carbon Emission Prediction (REQ-03)', () => {
    test('should predict emission for an enterprise', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/emission/predict`, {
        data: { enterpriseId: 1, predictMonths: 6 }
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toBeDefined()
      expect(body.data).toHaveProperty('enterpriseId')
      expect(body.data).toHaveProperty('confidence')
      expect(body.data).toHaveProperty('message')
      expect(body.data).toHaveProperty('predictions')
      expect(body.data).toHaveProperty('generatedAt')
      expect(typeof body.data.confidence).toBe('number')
      expect(Array.isArray(body.data.predictions)).toBeTruthy()
    })

    test('should return prediction data points with period and emission', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/emission/predict`, {
        data: { enterpriseId: 1, predictMonths: 3 }
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()

      if (body.data?.predictions?.length > 0) {
        const firstPoint = body.data.predictions[0]
        expect(firstPoint).toHaveProperty('period')
        expect(firstPoint).toHaveProperty('predictedEmission')
        expect(typeof firstPoint.predictedEmission).toBe('number')
      }
    })

    test('should work without optional predictMonths', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/emission/predict`, {
        data: { enterpriseId: 1 }
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.data).toHaveProperty('predictions')
    })

    test('should validate enterpriseId is required', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/emission/predict`, {
        data: { predictMonths: 6 }
      })

      expect(response.status()).toBeGreaterThanOrEqual(400)
    })

    test('should enforce rate limit on prediction endpoint', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      // Rate limit is 10 per 60 seconds — send 12 rapid requests
      const requests = Array(12).fill(null).map(() =>
        request.post(`${API_BASE}/emission/predict`, { data: { enterpriseId: 1 } })
      )

      const responses = await Promise.all(requests)
      const rateLimited = responses.some(r => r.status() === 429)

      expect(rateLimited).toBeTruthy()
    })
  })

  // ─── Permission Checks ────────────────────────────────────────────────────

  test.describe('Permission Checks', () => {
    test('unauthenticated user cannot access market prediction', async ({ page }) => {
      const marketPage = new MarketPredictionPage(page)
      await marketPage.goto()

      // Should redirect to login
      await expect(page).toHaveURL(/\/login/)
    })

    test('unauthenticated user cannot access enterprise inference', async ({ page }) => {
      const inferencePage = new EnterpriseInferencePage(page)
      await inferencePage.goto()

      // Should redirect to login
      await expect(page).toHaveURL(/\/login/)
    })
  })

  // ─── Rate Limiting (REQ-01) ───────────────────────────────────────────────

  test.describe('Rate Limiting (REQ-01)', () => {
    test('market prediction endpoints enforce rate limits', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      // Rate limit is 10 per 60 seconds — send 15 rapid requests
      const requests = Array(15).fill(null).map(() =>
        request.post(`${API_BASE}/ai/market/trend`, { params: { horizonDays: 30 } })
      )

      const responses = await Promise.all(requests)
      const rateLimited = responses.some(r => r.status() === 429)

      expect(rateLimited).toBeTruthy()
    })
  })
})
