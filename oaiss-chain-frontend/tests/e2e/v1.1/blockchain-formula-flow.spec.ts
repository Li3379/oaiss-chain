import { test, expect } from '@playwright/test'
import { loginViaApi, TEST_USERS } from '../fixtures/auth'
import { isMlServiceAvailable, isFabricAvailable, skipIfServiceUnavailable } from '../fixtures/test-env'

import type { APIRequestContext, APIResponse } from '@playwright/test'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
/**
 * 带认证的 API 请求。
 *
 * `/carbon/calculate/*` 要求登录（无 token 返回 401）。本文件早先直接
 * `request.post(...)` 没带 Authorization，于是每个用例都在
 * `expect(response.ok()).toBeTruthy()` 处失败，而报错只说 received=false，
 * 看不出是 401。
 *
 * 这里统一注入企业账号的 token，避免每个用例重复登录。
 */
// token 必须在文件范围内缓存。
//
// 最初每个用例各自登录一次，结果 6 个用例连发 6 次登录，撞上后端限流：
// 登录响应没有 data 字段，解构 accessToken 直接 TypeError。
// 缓存后整轮只登录一次。
let cachedEnterpriseToken: string | null = null

async function enterpriseToken(request: APIRequestContext): Promise<string> {
  if (cachedEnterpriseToken) return cachedEnterpriseToken
  const loginResp = await request.post(`${API_BASE}/auth/login`, {
    data: { username: TEST_USERS.enterprise.username, password: TEST_USERS.enterprise.password },
  })
  const payload = await loginResp.json()
  if (!payload?.data?.accessToken) {
    throw new Error(
      `企业账号登录失败：status=${loginResp.status()} body=${JSON.stringify(payload).slice(0, 200)}`,
    )
  }
  cachedEnterpriseToken = payload.data.accessToken as string
  return cachedEnterpriseToken
}

async function postAsEnterprise(
  request: APIRequestContext,
  path: string,
  data: unknown,
): Promise<APIResponse> {
  const token = await enterpriseToken(request)
  return request.post(`${API_BASE}${path}`, {
    data,
    headers: { Authorization: `Bearer ${token}` },
  })
}


test.describe('Flow: Blockchain & Carbon Formula', () => {

  // ── REQ-06: Carbon Formula Calculation (Power Generation 25-param) ──

  test.describe('Power Generation Formula (REQ-06)', () => {
    const powerGenerationPayload = {
      rawCoalFc: 1000,
      rawCoalNcv: 20.91,
      rawCoalCc: 26.0,
      rawCoalOf: 0.94,
      cleanedCoalFc: 500,
      cleanedCoalNcv: 23.01,
      cleanedCoalCc: 28.5,
      cleanedCoalOf: 0.93,
      otherWashedCoalFc: 200,
      otherWashedCoalNcv: 15.42,
      otherWashedCoalCc: 25.0,
      otherWashedCoalOf: 0.92,
      briquetteFc: 100,
      briquetteNcv: 17.54,
      briquetteCc: 26.5,
      briquetteOf: 0.90,
      otherCoalFc: 50,
      otherCoalNcv: 12.0,
      otherCoalCc: 22.0,
      otherCoalOf: 0.88,
      carbonateConsumed: 300,
      desulfEmissionFactor: 0.15,
      desulfConversionRate: 0.95,
      reportingYear: 2025,
      enterpriseName: 'Test Enterprise'
    }

    test('should calculate power generation emissions (25-param)', async ({ request }) => {
      const response = await postAsEnterprise(request, '/carbon/calculate/power-generation', powerGenerationPayload)

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toBeDefined()
      expect(body.data).toHaveProperty('totalEmission')
      expect(body.data).toHaveProperty('combustionEmission')
      expect(body.data).toHaveProperty('desulfurizationEmission')
      expect(body.data).toHaveProperty('fuelDetails')
      expect(body.data).toHaveProperty('reportingYear')
      expect(body.data).toHaveProperty('enterpriseName')
      expect(body.data).toHaveProperty('formulaReference')
      expect(typeof body.data.totalEmission).toBe('number')
      expect(body.data.totalEmission).toBeGreaterThan(0)
    })

    test('should return fuel details breakdown', async ({ request }) => {
      const response = await postAsEnterprise(request, '/carbon/calculate/power-generation', powerGenerationPayload)
      const body = await response.json()

      expect(body.data.fuelDetails).toBeInstanceOf(Array)
      expect(body.data.fuelDetails.length).toBeGreaterThan(0)

      const firstFuel = body.data.fuelDetails[0]
      expect(firstFuel).toHaveProperty('fuelType')
      expect(firstFuel).toHaveProperty('fuelConsumption')
      expect(firstFuel).toHaveProperty('emission')
    })

    test('should validate required fields', async ({ request }) => {
      const response = await postAsEnterprise(request, '/carbon/calculate/power-generation', { reportingYear: 2025 })

      expect(response.status()).toBeGreaterThanOrEqual(400)
    })
  })

  // ── REQ-06: Carbon Formula Calculation (Power Grid 9-param) ──

  test.describe('Power Grid Formula (REQ-06)', () => {
    const powerGridPayload = {
      transmissionVolume: 50000,
      lineLossRate: 0.06,
      gridEmissionFactor: 0.5810,
      generationVolume: 30000,
      importedElectricity: 10000,
      exportedElectricity: 5000,
      importEmissionFactor: 0.5,
      reportingYear: 2025,
      enterpriseName: 'Test Enterprise'
    }

    test('should calculate power grid emissions (9-param)', async ({ request }) => {
      const response = await postAsEnterprise(request, '/carbon/calculate/power-grid', powerGridPayload)

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toBeDefined()
      expect(body.data).toHaveProperty('totalEmission')
      expect(body.data).toHaveProperty('transmissionLossEmission')
      expect(body.data).toHaveProperty('importedEmission')
      expect(body.data).toHaveProperty('transmissionLoss')
      expect(body.data).toHaveProperty('formulaReference')
      expect(typeof body.data.totalEmission).toBe('number')
      expect(body.data.totalEmission).toBeGreaterThan(0)
    })

    test('should handle null optional fields', async ({ request }) => {
      const minimalPayload = {
        transmissionVolume: 50000,
        lineLossRate: 0.06,
        gridEmissionFactor: 0.5810,
        generationVolume: null,
        importedElectricity: null,
        exportedElectricity: null,
        importEmissionFactor: null,
        reportingYear: 2025,
        enterpriseName: 'Test Enterprise'
      }

      const response = await postAsEnterprise(request, '/carbon/calculate/power-grid', minimalPayload)

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.data).toHaveProperty('totalEmission')
    })

    test('should validate required fields', async ({ request }) => {
      const response = await postAsEnterprise(request, '/carbon/calculate/power-grid', { reportingYear: 2025 })

      expect(response.status()).toBeGreaterThanOrEqual(400)
    })
  })

  // ── REQ-06: Carbon Formula Calculator Frontend ──

  test.describe('Carbon Formula Calculator Frontend (REQ-06)', () => {
    test('enterprise user can access formula calculator page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-formula')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/carbon-formula/)
    })

    test('formula calculator page has tab navigation', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-formula')
      await page.waitForLoadState('networkidle')

      // 路由是 /enterprise/carbon-formula（早先写的 carbon-formula-calculator 不存在，
      // 会落到 404 页，自然数不到任何 tab）
      const tabs = page.getByRole('tab')
      const tabCount = await tabs.count()
      expect(tabCount).toBeGreaterThanOrEqual(2)
    })
  })

  // ── REQ-05: Blockchain Operations ──

  test.describe('Blockchain API (REQ-05)', () => {
    test.beforeAll(async () => {
      test.skip(!(await isFabricAvailable()), 'Fabric network not available')
    })
    test('should query latest blocks', async ({ request }) => {
      const loginResp = await request.post(`${API_BASE}/auth/login`, {
        data: { username: TEST_USERS.admin.username, password: TEST_USERS.admin.password },
      })
      const { accessToken } = (await loginResp.json()).data
      const response = await request.get(`${API_BASE}/blockchain/blocks/latest`, {
        params: { limit: 5 },
        headers: { Authorization: `Bearer ${accessToken}` },
      })

      // May return mock data or real data depending on Fabric availability
      expect([200, 503]).toContain(response.status())
    })

    test('should query latest transactions', async ({ request }) => {
      const loginResp = await request.post(`${API_BASE}/auth/login`, {
        data: { username: TEST_USERS.admin.username, password: TEST_USERS.admin.password },
      })
      const { accessToken } = (await loginResp.json()).data
      const response = await request.get(`${API_BASE}/blockchain/transactions/latest`, {
        params: { limit: 5 },
        headers: { Authorization: `Bearer ${accessToken}` },
      })

      expect([200, 503]).toContain(response.status())
    })
  })

  test.describe('Fabric CA Enrollment', () => {
    test.beforeAll(async () => {
      test.skip(!(await isFabricAvailable()), 'Fabric network not available')
    })

    test('CA enrollment status is included in blockchain status', async ({ request }) => {
      const loginResponse = await request.post(`${API_BASE}/auth/login`, {
        data: { username: TEST_USERS.admin.username, password: TEST_USERS.admin.password }
      })
      const loginBody = await loginResponse.json()
      const token = loginBody.data.accessToken

      const response = await request.get(`${API_BASE}/blockchain/status`, {
        headers: { Authorization: `Bearer ${token}` }
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
      expect(body.data).toHaveProperty('caEnabled')
      expect(typeof body.data.caEnabled).toBe('boolean')
    })
  })
})
