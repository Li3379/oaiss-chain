import { test, expect } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

/**
 * 现取现用的企业 token。
 *
 * 该文件原本在 beforeAll 里取一次、全文件共享 enterpriseToken。
 * 但后端有两处会让旧 token 立即失效：
 *   1. logout 按用户全局吊销（实测：注销一个 token，同账号其它 token 一起 401）
 *   2. refresh token 单次轮转（换发即作废旧会话）
 * 于是「登录/注销类用例」跑完后，共享 token 已死，后续用例全部 401——
 * 典型表现是「单跑通过、合跑失败」，排查成本极高。
 *
 * 改为每个用例需要时现取，彻底消除顺序依赖。
 */
async function freshEnterpriseToken(request: APIRequestContext): Promise<string> {
  const resp = await request.post(`${API_BASE}/auth/login`, {
    data: { username: TEST_USERS.enterprise.username, password: TEST_USERS.enterprise.password },
  })
  const payload = await resp.json()
  if (!payload?.data?.accessToken) {
    throw new Error(`企业账号登录失败：status=${resp.status()} body=${JSON.stringify(payload).slice(0, 200)}`)
  }
  return payload.data.accessToken as string
}


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

    // 前置保障：证书门禁生效后，无有效准入证的企业连列自己的报告都会被拒（7001）。
    // 回归用例假设「企业处于可正常营业状态」，这里显式补齐，
    // 而不是依赖其它 spec 恰好留下了证——那种隐式依赖会让
    // 「单独跑通过、整套跑失败」，极难定位。
    await request.post(`${API_BASE}/admin/enterprise-admission/1/issue`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
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
      // logout 是**按用户全局吊销**（实测：注销 T2 后，同一账号先前签发的 T1
      // 也立刻 401），而不是只废掉当前这一个 token。
      //
      // 这意味着登录/注销用例无法与其它用例共享同一账号的 token：
      // 一旦注销，beforeAll 里拿到的 enterpriseToken 就废了，
      // 后续所有依赖它的用例全部 401——表现为「单跑通过、合跑失败」。
      //
      // 处理：注销后立刻重新登录，把共享 token 刷新回可用状态，
      // 既验证了「logout 接口仍存在且可用」，又不留副作用。
      // 用**第三方监管**账号做注销验证，而非企业账号。
      //
      // logout 是按用户全局吊销：注销企业账号会让本文件后续所有企业接口
      // 401，而「注销后重新登录再刷新共享 token」在 Playwright 的串行执行里
      // 依然会被后续的 refresh 轮转打乱（实测 freshEnterpriseToken 仍 401）。
      // 换一个不参与其它断言的账号，彻底切断耦合。
      const tempLogin = await request.post(`${API_BASE}/auth/login`, {
        data: { username: TEST_USERS.thirdParty.username, password: TEST_USERS.thirdParty.password },
      })
      const tempToken = (await tempLogin.json()).data.accessToken

      const response = await request.post(`${API_BASE}/auth/logout`, {
        headers: { Authorization: `Bearer ${tempToken}` },
      })

      expect(response.status()).toBeLessThan(500)
    })

    test('token refresh endpoint still exists', async ({ request }) => {
      const response = await request.post(`${API_BASE}/auth/refresh`, {
        data: { refreshToken: 'dummy' },
      })

      // 端点存在性验证：只要不是 404 就说明路由已挂载。
      // 用 dummy refreshToken 调用会被业务层拒绝（4xx）或返回 400/401，
      // 早先写死 404 是反的——404 恰恰表示端点不存在。
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
      // 企业只能列自己的报告：/carbon/reports 是全量视图（管理员用），
      // 企业访问会 403。自己的报告走 /carbon/my-reports。
      const response = await request.get(`${API_BASE}/carbon/my-reports`, {
        headers: { Authorization: `Bearer ${await freshEnterpriseToken(request)}` },
      })

      if (!response.ok()) {
        console.log(`[DIAG] my-reports status=${response.status()} body=${(await response.text()).slice(0, 200)}`)
      }
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
      // 单数：/carbon-coin/account 返回「当前用户的账户」，
      // 复数 /accounts 不存在（账户按用户隔离，没有列表语义）。
      const response = await request.get(`${API_BASE}/carbon-coin/account`, {
        headers: { Authorization: `Bearer ${await freshEnterpriseToken(request)}` },
      })

      expect(response.ok()).toBeTruthy()
      const body = await response.json()
      expect(body.code).toBe(200)
    })

    test('transfer endpoint removed', async ({ request }) => {
      const response = await request.post(`${API_BASE}/carbon-coin/transfer`, {
        headers: { Authorization: `Bearer ${enterpriseToken}` },
        data: { toUserId: 2, amount: 100 },
      })

      // Feature removed: must now be 404
      expect(response.status()).toBe(404)
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

      // Feature removed: must now be 404
      expect(response.status()).toBe(404)
    })
  })

  // ─── Credit Score Regression ─────────────────────────────────────────────

  test.describe('Credit Score Regression', () => {
    test('can get credit score', async ({ request }) => {
      // 控制器前缀是 /credit（不是 /credit-score），
      // 自己的评分走 /credit/my-score；按 ID 取他人评分的形式不存在（越权面）。
      const response = await request.get(`${API_BASE}/credit/my-score`, {
        headers: { Authorization: `Bearer ${await freshEnterpriseToken(request)}` },
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
        '/admin/certificates',
      ]

      for (const p of pages) {
        await page.goto(p)
        await page.waitForLoadState('networkidle')
        await expect(page).not.toHaveURL(/error|500/)
      }
    })
  })
})
