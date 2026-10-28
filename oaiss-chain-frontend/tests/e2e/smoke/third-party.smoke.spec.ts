import { test, expect } from '@playwright/test'
import { loginViaToken } from '../fixtures/auth'
import { setupSmokeMock } from '../fixtures/api-mock'
import { Layout } from '../fixtures/page-objects/Layout'
import { MonitorPage } from '../fixtures/page-objects/MonitorPage'

test.describe('Role: Third Party - Smoke', () => {
  test.beforeEach(async ({ page }) => {
    setupSmokeMock(page, 'THIRD_PARTY')
    await loginViaToken(page, 'THIRD_PARTY')
  })

  test('sidebar menu complete', async ({ page }) => {
    await page.goto('/third-party/monitor')
    await page.waitForLoadState('networkidle')
    const layout = new Layout(page)
    await layout.expectSidebar(['监管面板'])
  })

  test('user info displayed', async ({ page }) => {
    await page.goto('/third-party/monitor')
    await page.waitForLoadState('networkidle')
    const layout = new Layout(page)
    await layout.expectUserInfo('thirdparty', '第三方监管')
  })

  test.describe('Monitor Panel', () => {
    test('page loads with org info', async ({ page }) => {
      const po = new MonitorPage(page)
      await po.goto()
      await po.expectLoaded()
      await po.expectOrgInfo()
    })

    test('statistics section visible', async ({ page }) => {
      const po = new MonitorPage(page)
      await po.goto()
      await po.expectStatistics()
    })
  })
})
