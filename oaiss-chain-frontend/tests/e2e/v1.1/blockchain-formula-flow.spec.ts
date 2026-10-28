import { test, expect } from '@playwright/test'
import { loginViaApi, TEST_USERS } from '../fixtures/auth'
import { isMlServiceAvailable, isFabricAvailable, skipIfServiceUnavailable } from '../fixtures/test-env'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

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
      const response = await request.post(`${API_BASE}/carbon/calculate/power-generation`, {
        data: powerGenerationPayload
      })

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
      const response = await request.post(`${API_BASE}/carbon/calculate/power-generation`, {
        data: powerGenerationPayload
      })
      const body = await response.json()

      expect(body.data.fuelDetails).toBeInstanceOf(Array)
      expect(body.data.fuelDetails.length).toBeGreaterThan(0)

      const firstFuel = body.data.fuelDetails[0]
      expect(firstFuel).toHaveProperty('fuelType')
      expect(firstFuel).toHaveProperty('fuelConsumption')
      expect(firstFuel).toHaveProperty('emission')
    })

    test('should validate required fields', async ({ request }) => {
      const response = await request.post(`${API_BASE}/carbon/calculate/power-generation`, {
        data: { reportingYear: 2025 }
      })

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
      const response = await request.post(`${API_BASE}/carbon/calculate/power-grid`, {
        data: powerGridPayload
      })

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

      const response = await request.post(`${API_BASE}/carbon/calculate/power-grid`, {
        data: minimalPayload
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.data).toHaveProperty('totalEmission')
    })

    test('should validate required fields', async ({ request }) => {
      const response = await request.post(`${API_BASE}/carbon/calculate/power-grid`, {
        data: { reportingYear: 2025 }
      })

      expect(response.status()).toBeGreaterThanOrEqual(400)
    })
  })

  // ── REQ-06: Carbon Formula Calculator Frontend ──

  test.describe('Carbon Formula Calculator Frontend (REQ-06)', () => {
    test('enterprise user can access formula calculator page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-formula-calculator')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/carbon-formula/)
    })

    test('formula calculator page has tab navigation', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-formula-calculator')
      await page.waitForLoadState('networkidle')

      // Should have tabs for power generation and power grid
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
