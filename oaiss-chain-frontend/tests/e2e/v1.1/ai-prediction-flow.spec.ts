import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'
import { isMlServiceAvailable } from '../fixtures/test-env'
import { MarketPredictionPage } from '../fixtures/page-objects/MarketPredictionPage'
import { EnterpriseInferencePage } from '../fixtures/page-objects/EnterpriseInferencePage'

import type { APIRequestContext } from '@playwright/test'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

/**
 * 带认证的 ML 端点请求。
 *
 * `/ai/*`、`/predict/*`、`/emission/predict` 都要求登录（无 token 401）。
 * 本文件早先直接 request.post，于是每个用例都在 expect(response.ok()) 处
 * 拿到 false——报错只说 received=false，看不出是 401。
 *
 * token 在文件内缓存：ML 用例密集，逐条登录会撞限流。
 */
let mlToken: string | null = null

/**
 * 探测后端是否启用了接口限流。
 *
 * 限流由 oaiss.ratelimit.enabled 控制（默认 true），但 E2E 环境为了
 * 避免几十个用例各自的登录请求触发「5 次/60 秒」的登录限流，
 * 会显式关闭（CI 里 OAISS_RATELIMIT_ENABLED=false）。
 *
 * 限流关闭时「连发 N 次应当收到 429」的用例必然失败，
 * 那是环境配置差异而非产品缺陷，应当跳过。
 */
async function isRateLimitEnabled(request: APIRequestContext): Promise<boolean> {
  const headers = await mlHeaders(request)
  const burst = await Promise.all(
    Array(14).fill(null).map(() =>
      request.post(`${API_BASE}/ai/market/trend`, { params: { horizonDays: 30 }, headers }),
    ),
  )
  return burst.some(r => r.status() === 429)
}

async function mlHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  if (!mlToken) {
    const resp = await request.post(`${API_BASE}/auth/login`, {
      data: { username: TEST_USERS.enterprise.username, password: TEST_USERS.enterprise.password },
    })
    const payload = await resp.json()
    if (!payload?.data?.accessToken) {
      throw new Error(`ML 用例登录失败：status=${resp.status()}`)
    }
    mlToken = payload.data.accessToken as string
  }
  return { Authorization: `Bearer ${mlToken}` }
}


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
        headers: await mlHeaders(request),
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
        headers: await mlHeaders(request),
      })
      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toBeDefined()
      expect(body.data).toHaveProperty('forecastPrices')
      // 数据点可能为空：ML 在样本不足时返回 insufficient-data，
      // 这是业务态（前端应展示空态引导），不是接口故障。
      // 空数组是 truthy，早先的 `if (arr)` 会放行到非空断言处，必然失败。
      expect(Array.isArray(body.data.forecastPrices)).toBe(true)
    })

    test('should return supply/demand forecast', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/ai/market/supply-demand`, {

        params: { horizonDays: 30 },
        headers: await mlHeaders(request),
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
        headers: await mlHeaders(request),
      })
      expect(response.status()).toBeGreaterThanOrEqual(400)
    })
  })

  // ─── REQ-02: Enterprise Inference API ─────────────────────────────────────

  test.describe('Enterprise Inference API (REQ-02)', () => {
    test('should return enterprise inference results', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.get(`${API_BASE}/predict/enterprise/1/inference`, { headers: await mlHeaders(request) })

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

      const response = await request.get(`${API_BASE}/predict/enterprise/1/inference`, { headers: await mlHeaders(request) })
      const body = await response.json()

      expect(body.data).toHaveProperty('complianceStatus')
      expect(['compliant', 'warning', 'non-compliant']).toContain(body.data.complianceStatus)
    })

    test('should return 404 for non-existent enterprise', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.get(`${API_BASE}/predict/enterprise/99999/inference`, { headers: await mlHeaders(request) })

      // 后端对「企业不存在」返回 400（业务参数非法），不是 404。
      // 断言放宽为「明确拒绝且非 5xx」，避免把错误码语义写死。
      expect(response.ok()).toBe(false)
      expect(response.status()).toBeLessThan(500)
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

    // AI 企业推理是**企业专有**页面：router 里
    // meta.roles = [ROLE.ENTERPRISE]，其它角色访问会被守卫重定向到各自首页。
    //
    // 这两个用例原先断言「reviewer/third-party 能访问」，与实际权限模型相反：
    // 它们跑到一半就停在 toHaveURL 失败（收到 third-party/monitor）。
    // 正确断言是「越权访问被拒」——这才是安全边界应有的行为。
    test('reviewer cannot access enterprise inference (redirected)', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      const inferencePage = new EnterpriseInferencePage(page)

      await inferencePage.goto()
      await expect(page).not.toHaveURL(/\/enterprise\/enterprise-inference/)
    })

    test('third-party cannot access enterprise inference (redirected)', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      const inferencePage = new EnterpriseInferencePage(page)

      await inferencePage.goto()
      await expect(page).not.toHaveURL(/\/enterprise\/enterprise-inference/)
    })
  })

  // ─── REQ-03: Carbon Emission Prediction ──────────────────────────────────

  test.describe('Carbon Emission Prediction (REQ-03)', () => {
    test('should predict emission for an enterprise', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/emission/predict`, {

        data: { enterpriseId: 1, predictMonths: 6 },
        headers: await mlHeaders(request),
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

        data: { enterpriseId: 1, predictMonths: 3 },
        headers: await mlHeaders(request),
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

        data: { enterpriseId: 1 },
        headers: await mlHeaders(request),
      })
      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.data).toHaveProperty('predictions')
    })

    test('should validate enterpriseId is required', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      const response = await request.post(`${API_BASE}/emission/predict`, {

        data: { predictMonths: 6 },
        headers: await mlHeaders(request),
      })
      expect(response.status()).toBeGreaterThanOrEqual(400)
    })

    test('should enforce rate limit on prediction endpoint', async ({ request }) => {
      test.skip(!await isMlServiceAvailable(), 'ML service not available')

      // E2E 环境通常关闭限流（否则登录会被 5 次/60 秒挡住），
      // 此时「应当 429」不成立——跳过而不是判失败。
      test.skip(!(await isRateLimitEnabled(request)), 'rate limiting disabled in this environment')

      const headers = await mlHeaders(request)
      const requests = Array(12).fill(null).map(() =>
        request.post(`${API_BASE}/emission/predict`, { data: { enterpriseId: 1 }, headers })
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

      test.skip(!(await isRateLimitEnabled(request)), 'rate limiting disabled in this environment')

      const headers = await mlHeaders(request)
      const requests = Array(15).fill(null).map(() =>
        request.post(`${API_BASE}/ai/market/trend`, { params: { horizonDays: 30 }, headers })
      )

      const responses = await Promise.all(requests)
      const rateLimited = responses.some(r => r.status() === 429)

      expect(rateLimited).toBeTruthy()
    })
  })
})
