import { test, expect } from '@playwright/test'
import { TEST_USERS, loginViaApi } from '../fixtures/auth'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

test.describe('Auth Flow', () => {
  test.describe('Login', () => {
    test('enterprise user can login and reach dashboard', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/enterprise\/carbon\/upload/)
    })

    test('admin user can login and reach admin panel', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.admin.username, TEST_USERS.admin.password)
      await page.goto('/admin/system/users')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/admin\/system\/users/)
    })

    test('reviewer can login and reach audit list', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.reviewer.username, TEST_USERS.reviewer.password)
      await page.goto('/auditor/audit/list')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/auditor\/audit\/list/)
    })

    test('admin can login and reach verify list', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.admin.username, TEST_USERS.admin.password)
      await page.goto('/admin/verify/list')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/admin\/verify\/list/)
    })

    test('third-party can login and reach monitor page', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.thirdParty.username, TEST_USERS.thirdParty.password)
      await page.goto('/third-party/monitor')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/third-party\/monitor/)
    })
  })

  test.describe('Wrong password', () => {
    test('login fails with wrong password via API', async ({ page }) => {
      const response = await page.request.post(`${API_BASE}/auth/login`, {
        data: {
          username: TEST_USERS.enterprise.username,
          password: 'wrong-password',
          captchaKey: 'test',
          captcha: '1234',
        },
      })

      expect(response.status()).toBeGreaterThanOrEqual(400)
    })
  })

  test.describe('Logout', () => {
    test('enterprise user can logout', async ({ page }) => {
      const token = await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)

      const response = await page.request.post(`${API_BASE}/auth/logout`, {
        headers: { Authorization: `Bearer ${token}` },
      })

      expect(response.status()).toBeLessThan(500)
    })
  })

  test.describe('Permission checks', () => {
    test('unauthenticated user is redirected to login', async ({ page }) => {
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/login/)
    })

    test('unauthenticated user cannot access admin panel', async ({ page }) => {
      await page.goto('/admin/system/users')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(/\/login/)
    })

    test('enterprise user cannot access admin panel', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/admin/system/users')
      await page.waitForLoadState('networkidle')

      await expect(page).not.toHaveURL(/\/admin\/system\/users/)
    })

    test('enterprise user cannot access reviewer audit list', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/auditor/audit/list')
      await page.waitForLoadState('networkidle')

      await expect(page).not.toHaveURL(/\/auditor\/audit\/list/)
    })

    test('enterprise user cannot access third-party monitor', async ({ page }) => {
      await loginViaApi(page, TEST_USERS.enterprise.username, TEST_USERS.enterprise.password)
      await page.goto('/third-party/monitor')
      await page.waitForLoadState('networkidle')

      await expect(page).not.toHaveURL(/\/third-party\/monitor/)
    })
  })
})
