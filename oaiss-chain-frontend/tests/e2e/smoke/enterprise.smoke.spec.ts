import { test, expect } from '@playwright/test'
import { loginViaToken } from '../fixtures/auth'
import { setupSmokeMock } from '../fixtures/api-mock'
import { Layout } from '../fixtures/page-objects/Layout'
import { CarbonUploadPage } from '../fixtures/page-objects/CarbonUploadPage'
import { AuctionMarketPage } from '../fixtures/page-objects/AuctionMarketPage'
import { P2PTradePage } from '../fixtures/page-objects/P2PTradePage'
import { CarbonNeutralPage } from '../fixtures/page-objects/CarbonNeutralPage'
import { CreditScorePage } from '../fixtures/page-objects/CreditScorePage'
import { CarbonCoinPage } from '../fixtures/page-objects/CarbonCoinPage'

test.describe('Role: Enterprise - Smoke', () => {
  test.beforeEach(async ({ page }) => {
    setupSmokeMock(page, 'ENTERPRISE')
    await loginViaToken(page, 'ENTERPRISE')
  })

  test('sidebar menu complete', async ({ page }) => {
    await page.goto('/enterprise/carbon/upload')
    await page.waitForLoadState('networkidle')
    const layout = new Layout(page)
    await layout.expectSidebar([
      '碳核算',
      'P2P订单管理',
      '碳交易',
      '本公司信息',
      '信誉评分',
      '碳币账户',
      '区块链',
      '碳中和',
      '个人中心',
    ])
  })

  test('user info displayed', async ({ page }) => {
    await page.goto('/enterprise/carbon/upload')
    await page.waitForLoadState('networkidle')
    const layout = new Layout(page)
    await layout.expectUserInfo('enterprise001', '企业用户')
  })

  test.describe('Carbon Upload', () => {
    test('page loads with correct route', async ({ page }) => {
      const po = new CarbonUploadPage(page)
      await po.goto()
      await po.expectLoaded()
    })

    test('breadcrumb correct', async ({ page }) => {
      await page.goto('/enterprise/carbon/upload')
      await page.waitForLoadState('networkidle')
      const layout = new Layout(page)
      await layout.expectBreadcrumb('碳核算', '上传审核')
    })

    test('search form exists', async ({ page }) => {
      const po = new CarbonUploadPage(page)
      await po.goto()
      await po.expectSearchForm()
    })

    test('table headers correct', async ({ page }) => {
      const po = new CarbonUploadPage(page)
      await po.goto()
      await po.expectTableHeaders()
    })

    test('empty state shown', async ({ page }) => {
      const po = new CarbonUploadPage(page)
      await po.goto()
      await po.expectEmptyState()
    })

    test('create button exists', async ({ page }) => {
      const po = new CarbonUploadPage(page)
      await po.goto()
      await expect(page.getByRole('button', { name: '创建' })).toBeVisible()
    })
  })

  test.describe('Auction Market', () => {
    test('page loads', async ({ page }) => {
      const po = new AuctionMarketPage(page)
      await po.goto()
      await po.expectLoaded()
    })

    test('tabs and order form exist', async ({ page }) => {
      const po = new AuctionMarketPage(page)
      await po.goto()
      await po.expectTabs()
    })
  })

  test.describe('P2P Trade', () => {
    test('page loads', async ({ page }) => {
      const po = new P2PTradePage(page)
      await po.goto()
      await po.expectLoaded()
    })
  })

  test.describe('Carbon Neutral', () => {
    test('page loads', async ({ page }) => {
      const po = new CarbonNeutralPage(page)
      await po.goto()
      await po.expectLoaded()
    })
  })

  test.describe('Credit Score', () => {
    test('page loads with score display', async ({ page }) => {
      const po = new CreditScorePage(page)
      await po.goto()
      await po.expectLoaded()
      await po.expectScoreDisplay()
    })
  })

  test.describe('Carbon Coin', () => {
    test('page loads with balance', async ({ page }) => {
      const po = new CarbonCoinPage(page)
      await po.goto()
      await po.expectLoaded()
      await po.expectBalance()
    })
  })

  test.describe('Blockchain Browser', () => {
    test('page loads', async ({ page }) => {
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')
      await expect(page).toHaveURL(/\/enterprise\/blockchain\/browser/)
    })
  })

  test.describe('Emission Data', () => {
    test('page loads', async ({ page }) => {
      await page.goto('/enterprise/emission/data')
      await page.waitForLoadState('networkidle')
      await expect(page).toHaveURL(/\/enterprise\/emission\/data/)
    })
  })

  test.describe('User Profile', () => {
    test('page loads', async ({ page }) => {
      await page.goto('/enterprise/user/profile')
      await page.waitForLoadState('networkidle')
      await expect(page).toHaveURL(/\/enterprise\/user\/profile/)
    })
  })
})
