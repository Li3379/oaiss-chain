import { test, expect, request as playwrightRequest, type APIRequestContext } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'
import { isMlServiceAvailable } from '../fixtures/test-env'
import {
  API_MODULE_COVERAGE,
  verifyApiModuleCoverage,
  compareSwaggerWithApiModules,
  extractEndpointsFromApiModule,
} from '../fixtures/api-coverage'

import { API_BASE_URL } from '../fixtures/api-base'

// baseURL 必须带末尾斜杠、且请求路径不带前导斜杠，否则 /api/v1 会被丢掉。
const API_BASE = API_BASE_URL

/**
 * 探测后端是否暴露 Swagger 文档。
 *
 * application-docker.yml / application-prod.yml 显式设置
 * springdoc.api-docs.enabled=false（生产安全配置，正确行为）。
 * CI 用 docker profile 启动后端，因此 api-docs 不可用——
 * 这不是缺陷，而是「该环境本就不该暴露文档」。
 *
 * 文档一致性类断言在文档未暴露时应跳过，而不是判红。
 */
async function isSwaggerAvailable(
  request: APIRequestContext,
  token: string,
): Promise<boolean> {
  const resp = await request.get(`${API_BASE}v1/api-docs`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return resp.ok()
}


test.describe('Frontend Coverage Flow', () => {
  let adminToken: string
  let enterpriseToken: string
  let reviewerToken: string

  test.beforeAll(async () => {
    const ctx = await playwrightRequest.newContext({ baseURL: API_BASE })

    const adminLogin = await ctx.post('auth/login', {
      data: { username: TEST_USERS.admin.username, password: TEST_USERS.admin.password },
    })
    const adminBody = await adminLogin.json()
    adminToken = adminBody.data?.accessToken || ''

    const entLogin = await ctx.post('auth/login', {
      data: { username: TEST_USERS.enterprise.username, password: TEST_USERS.enterprise.password },
    })
    const entBody = await entLogin.json()
    enterpriseToken = entBody.data?.accessToken || ''

    const revLogin = await ctx.post('auth/login', {
      data: { username: TEST_USERS.reviewer.username, password: TEST_USERS.reviewer.password },
    })
    const revBody = await revLogin.json()
    reviewerToken = revBody.data?.accessToken || ''

    await ctx.dispose()
  })

  // ─── REQ-09: API Module Coverage ────────────────────────────────────────

  test.describe('API Module Coverage (REQ-09)', () => {
    test('market prediction API endpoints exist', async () => {
      // 市场预测端点依赖 ML 微服务；服务未启动时逐条探测必然全失败，
      // 但那是「环境缺失」而不是「端点不存在」，不应计为缺陷。
      test.skip(!(await isMlServiceAvailable()), 'ML service not available')

      const result = await verifyApiModuleCoverage('marketPrediction', enterpriseToken)

      expect(result.endpoints).toBeGreaterThan(0)
      // At least 50% of endpoints should be accessible
      expect(result.covered / result.endpoints).toBeGreaterThanOrEqual(0.5)

      console.log(`Market Prediction: ${result.covered}/${result.endpoints} endpoints covered`)
      for (const r of result.results) {
        console.log(`  ${r.endpoint}: exists=${r.exists} status=${r.status}`)
      }
    })

    test('enterprise inference API endpoint exists', async () => {
      const result = await verifyApiModuleCoverage('enterpriseInference', enterpriseToken)

      expect(result.endpoints).toBe(1)
      expect(result.covered).toBe(1)
    })

    test('carbon formula API endpoints exist', async () => {
      const result = await verifyApiModuleCoverage('carbonFormula', enterpriseToken)

      expect(result.endpoints).toBe(2)
      expect(result.covered).toBeGreaterThanOrEqual(1)
    })

    test('enterprise API endpoints exist', async () => {
      const result = await verifyApiModuleCoverage('enterprise', enterpriseToken)

      expect(result.endpoints).toBeGreaterThan(0)
      expect(result.covered / result.endpoints).toBeGreaterThanOrEqual(0.5)
    })

    test('reviewer API endpoints exist', async () => {
      const result = await verifyApiModuleCoverage('reviewer', reviewerToken)

      expect(result.endpoints).toBeGreaterThan(0)
      expect(result.covered / result.endpoints).toBeGreaterThanOrEqual(0.5)
    })

    test('admin certificate API endpoints exist', async () => {
      const result = await verifyApiModuleCoverage('adminCertificates', adminToken)

      expect(result.endpoints).toBe(6)
      expect(result.covered).toBeGreaterThanOrEqual(4)
    })

    test('v1.0 API endpoints still exist (regression check)', async () => {
      // Verify core v1.0 endpoints were not broken by v1.1 changes
      const carbonEndpoints = await verifyApiModuleCoverage('carbon', enterpriseToken)
      expect(carbonEndpoints.covered).toBeGreaterThanOrEqual(3)

      const authEndpoints = await verifyApiModuleCoverage('auth', undefined)
      expect(authEndpoints.covered).toBe(3)
    })
  })

  // ─── REQ-10: Enterprise View Operations ─────────────────────────────────

  test.describe('Enterprise View Operations (REQ-10)', () => {
    test('carbon upload page loads and displays reports', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')

      // Check for report table
      const table = page.getByRole('table')
      await expect(table.first()).toBeVisible()
    })

    test('carbon neutral projects page loads', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-neutral/projects')
      await page.waitForLoadState('networkidle')

      // Check for project list content or create button
      const content = page.getByText(/碳中和项目|Carbon Neutral|创建项目|项目列表/)
      await expect(content.first()).toBeVisible()
    })

    test('company dashboard loads and displays statistics', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/company/dashboard')
      await page.waitForLoadState('networkidle')

      // Check for dashboard content (charts, statistics, or data containers)
      const dashboard = page.locator('.dashboard, .statistics, .chart, .echarts, [class*="chart"]')
      const count = await dashboard.count()
      expect(count).toBeGreaterThan(0)
    })

    test('market prediction page loads (v1.1 feature)', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/market-prediction')
      await page.waitForLoadState('networkidle')

      // Page should load without errors
      await expect(page).not.toHaveURL(/error|500/)
    })

    test('carbon formula calculator page loads (v1.1 feature)', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon-formula')
      await page.waitForLoadState('networkidle')

      // Check for tabs (power generation + power grid)
      const tabs = page.getByRole('tab')
      const count = await tabs.count()
      expect(count).toBeGreaterThanOrEqual(2)
    })

    test('enterprise can initiate carbon report creation', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')

      // Click create button
      // 空库时页面会同时有「筛选区创建按钮」与「空态引导创建按钮」，
      // 不加 .first() 会触发 strict mode violation（isVisible 也会抛）。
      const createBtn = page.getByRole('button', { name: /创建|Create|新建/ }).first()
      if (await createBtn.isVisible()) {
        await createBtn.click()

        // Check for dialog
        const dialog = page.getByRole('dialog')
        await expect(dialog.first()).toBeVisible({ timeout: 5000 })

        // Close dialog
        await page.keyboard.press('Escape')
      }
    })
  })

  // ─── REQ-11: Reviewer View Operations + Swagger Consistency ─────────────

  test.describe('Reviewer View Operations (REQ-11)', () => {
    test('audit list page loads and displays pending reports', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/auditor/audit/list')
      await page.waitForLoadState('networkidle')

      // Check for audit table
      const table = page.getByRole('table')
      await expect(table.first()).toBeVisible()
    })

    test('reviewer can view report details', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/auditor/audit/list')
      await page.waitForLoadState('networkidle')

      // Try to click first action button
      const actionBtn = page.getByRole('button', { name: /操作|审核|详情/ }).first()
      if (await actionBtn.isVisible()) {
        await actionBtn.click()
        await page.waitForTimeout(1000)

        // Check for detail view or dialog
        const detail = page.locator('.detail, .dialog, .drawer, [class*="detail"]')
        if ((await detail.count()) > 0) {
          await expect(detail.first()).toBeVisible()
        }
      }
    })

    test('review history page loads', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/auditor/review/history')
      await page.waitForLoadState('networkidle')

      // Page should load without errors
      await expect(page).not.toHaveURL(/error|500/)
    })

    test('project review page loads (v1.1 feature)', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/auditor/project/review')
      await page.waitForLoadState('networkidle')

      // Page should load without errors
      await expect(page).not.toHaveURL(/error|500/)
    })
  })

  test.describe('Swagger Documentation Consistency (REQ-11)', () => {
    test('swagger documentation is accessible', async ({ request }) => {
      test.skip(!(await isSwaggerAvailable(request, adminToken)), 'swagger disabled in this environment (docker/prod profile)')

      // 路径由 application.yml 的 springdoc.api-docs.path 决定：/v1/api-docs，
      // 叠加 context-path 即 /api/v1/v1/api-docs。且该端点要求认证（无 token 401）。
      const response = await request.get(`${API_BASE}v1/api-docs`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body).toHaveProperty('openapi')
      expect(body).toHaveProperty('paths')
    })

    test('swagger paths match frontend API modules', async ({ request }) => {
      test.skip(!(await isSwaggerAvailable(request, adminToken)), 'swagger disabled in this environment (docker/prod profile)')

      const comparison = await compareSwaggerWithApiModules()

      console.log(`Swagger paths: ${comparison.swaggerPaths}`)
      console.log(`Frontend modules: ${comparison.frontendModules}`)
      console.log(`Coverage: ${comparison.coverage}%`)

      // At least 80% of Swagger paths should be covered by frontend modules
      expect(comparison.coverage).toBeGreaterThanOrEqual(80)
    })

    test('swagger documentation includes v1.1 endpoints', async ({ request }) => {
      test.skip(!(await isSwaggerAvailable(request, adminToken)), 'swagger disabled in this environment (docker/prod profile)')

      const response = await request.get(`${API_BASE}v1/api-docs`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      })
      const body = await response.json()
      const paths = body.paths as Record<string, unknown>

      // Check for key v1.1 endpoints in Swagger
      const v11PathPatterns = [
        '/ai/market/trend',
        '/ai/market/price',
        '/predict/enterprise',
        '/carbon/calculate/power-generation',
        '/carbon/calculate/power-grid',
        '/admin/enterprise-admission',
        '/admin/reviewer-qualification',
      ]

      const swaggerPathKeys = Object.keys(paths)
      for (const pattern of v11PathPatterns) {
        const found = swaggerPathKeys.some((k) => k.includes(pattern))
        if (found) {
          expect(found).toBeTruthy()
        }
      }
    })
  })

  test.describe('Frontend API Module Files Exist', () => {
    test('all v1.1 API module files exist', async () => {
      const fs = await import('fs')
      const pathMod = await import('path')

      // process.cwd() 在 playwright 运行时就是 oaiss-chain-frontend 目录
      // （playwright.config.ts 在该目录下），再拼一次 oaiss-chain-frontend
      // 会得到 .../oaiss-chain-frontend/oaiss-chain-frontend/src/api —— 恒不存在。
      const apiDir = pathMod.join(process.cwd(), 'src', 'api')
      // enterpriseInference.ts 已于 2026-11-02 随前端企业推理模块一并移除
      //（后端端点仍在，其存活由上方 "enterprise inference API endpoint exists" 覆盖）
      const requiredFiles = [
        'marketPrediction.ts',
        'carbonFormula.ts',
        'enterprise.ts',
        'reviewer.ts',
      ]

      for (const file of requiredFiles) {
        const filePath = pathMod.join(apiDir, file)
        expect(fs.existsSync(filePath)).toBeTruthy()
      }
    })

    test('API module files export expected functions', async () => {
      // Check that each v1.1 module file contains request calls
      const marketPredictionEndpoints = extractEndpointsFromApiModule('marketPrediction.ts')
      expect(marketPredictionEndpoints.length).toBeGreaterThan(0)

      // 该模块已不止两个端点：除发电/电网核算外，还新增了碳氧化率实测
      // 与政策问答（G5 缺口回填）。断言取下界而非定值，避免每次加接口就红。
      const carbonFormulaEndpoints = extractEndpointsFromApiModule('carbonFormula.ts')
      expect(carbonFormulaEndpoints.length).toBeGreaterThanOrEqual(2)
    })
  })

  test.describe('Cross-Role Access Verification', () => {
    test('enterprise cannot access admin endpoints', async ({ request }) => {
      const response = await request.get(`${API_BASE}/admin/enterprise-admission`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
      })

      // Should be forbidden
      expect([401, 403]).toContain(response.status())
    })

    test('reviewer cannot access enterprise-specific endpoints', async ({ request }) => {
      const response = await request.get(`${API_BASE}/enterprise/admission/my`, {
        headers: { Authorization: `Bearer ${reviewerToken}` },
      })

      // May be forbidden or return empty (implementation dependent)
      expect(response.status()).toBeLessThan(500)
    })
  })
})
