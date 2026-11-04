import { test, expect } from '@playwright/test'
import { buildStorageState, loginViaToken } from '../fixtures/auth'

const MOCK_BLOCKS = {
  content: [
    {
      blockNumber: 100,
      blockHash: '0xabc123def456789',
      blockType: 'REGULAR',
      transactionCount: 5,
      miner: 'Org1MSP',
      timestamp: '2026-05-16T10:00:00Z',
    },
    {
      blockNumber: 99,
      blockHash: '0xdef789ghi012345',
      blockType: 'GENESIS',
      transactionCount: 1,
      miner: 'Org2MSP',
      timestamp: '2026-05-15T08:00:00Z',
    },
  ],
  totalElements: 2,
}

const MOCK_TRANSACTIONS = {
  content: [
    {
      txHash: '0xtx001abc',
      blockNumber: 100,
      // 表格现在展示链上真实可得的字段：发起组织 MSP 与调用的链码函数。
      // fromAddress/toAddress/amount 是后端从不产出的占位列，已移除。
      senderMsp: 'Org1MSP',
      function: 'CreateCarbonReportV2',
      status: 'VALID',
      timestamp: '2026-05-16T10:05:00Z',
    },
    {
      txHash: '0xtx002def',
      blockNumber: 100,
      senderMsp: 'Org2MSP',
      function: 'CreateTradeRecord',
      status: 'VALID',
      timestamp: '2026-05-16T10:10:00Z',
    },
  ],
  totalElements: 2,
}

function mockBlockchainApi(page, blocks = MOCK_BLOCKS, transactions = MOCK_TRANSACTIONS) {
  page.route(/\/api\/v1\/blockchain\//, (route) => {
    const url = route.request().url()
    let data
    if (url.includes('/blocks/latest')) {
      data = blocks
    } else if (url.includes('/transactions')) {
      data = transactions
    } else {
      data = { connected: true, networkName: 'test-network' }
    }
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'ok', data }),
    })
  })
}

function mockEmptyBlockchainApi(page) {
  mockBlockchainApi(page, { content: [], totalElements: 0 }, { content: [], totalElements: 0 })
}

test.describe('D9: Blockchain Browser', () => {
  test.describe('Blocks Tab', () => {
    test.use({ storageState: buildStorageState('THIRD_PARTY') })

    test.beforeEach(async ({ page }) => {
      mockBlockchainApi(page)
      await loginViaToken(page, 'THIRD_PARTY')
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')
    })

    test('blockchain page renders with correct URL', async ({ page }) => {
      await expect(page).toHaveURL(/\/enterprise\/blockchain\/browser/)
      // 页面里有多个 <header>（壳层顶栏 + 各 DataPanel 内部），
      // 不加限定会触发 strict mode violation。壳层顶栏是第一个。
      await expect(page.locator('header').first()).toBeVisible()
    })

    test('blocks table displays mock data', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper').first()
      await expect(tableBody).toBeVisible()
      await expect(tableBody.locator('tbody tr')).toHaveCount(2)
    })

    test('block data shows block number and hash', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper').first()
      await expect(tableBody).toContainText('100')
      await expect(tableBody).toContainText('0xabc123def456789')
    })

    test('block type renders as tag element', async ({ page }) => {
      const tableBody = page.locator('.el-table__body-wrapper').first()
      await expect(tableBody.locator('.el-tag').first()).toBeVisible()
    })

    test('blocks pagination shows correct total', async ({ page }) => {
      const pagination = page.locator('.el-pagination').first()
      await expect(pagination).toContainText('2')
    })
  })

  test.describe('Transactions Tab', () => {
    test.use({ storageState: buildStorageState('THIRD_PARTY') })

    test.beforeEach(async ({ page }) => {
      mockBlockchainApi(page)
      await loginViaToken(page, 'THIRD_PARTY')
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')
    })

    test('can switch to transactions tab', async ({ page }) => {
      const tabs = page.locator('.el-tabs__item')
      const txTab = tabs.nth(1)
      await txTab.click()
      await expect(txTab).toHaveClass(/is-active/)
    })

    test('transactions table displays after tab switch', async ({ page }) => {
      const tabs = page.locator('.el-tabs__item')
      await tabs.nth(1).click()
      const tableBody = // 旧的 :not([style*=display:none]) 依赖 el-tabs 的内联样式实现，
      // 换版本即失效。直接取「当前可见的第一个表格体」更稳。
      page.locator('.el-table__body-wrapper:visible').first()
      await expect(tableBody).toBeVisible()
    })

    test('transaction data shows tx hash and chaincode function', async ({ page }) => {
      const tabs = page.locator('.el-tabs__item')
      await tabs.nth(1).click()
      const tableBody = // 旧的 :not([style*=display:none]) 依赖 el-tabs 的内联样式实现，
      // 换版本即失效。直接取「当前可见的第一个表格体」更稳。
      page.locator('.el-table__body-wrapper:visible').first()
      await expect(tableBody).toContainText('0xtx001abc')
      await expect(tableBody).toContainText('CreateCarbonReportV2')
    })

    test('transaction status renders as tag', async ({ page }) => {
      const tabs = page.locator('.el-tabs__item')
      await tabs.nth(1).click()
      const tableBody = // 旧的 :not([style*=display:none]) 依赖 el-tabs 的内联样式实现，
      // 换版本即失效。直接取「当前可见的第一个表格体」更稳。
      page.locator('.el-table__body-wrapper:visible').first()
      await expect(tableBody.locator('.el-tag').first()).toBeVisible()
    })
  })

  test.describe('Empty State', () => {
    test.use({ storageState: buildStorageState('THIRD_PARTY') })

    test.beforeEach(async ({ page }) => {
      mockEmptyBlockchainApi(page)
      await loginViaToken(page, 'THIRD_PARTY')
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')
    })

    test('shows empty state when no blocks', async ({ page }) => {
      const table = page.getByRole('table').first()
      await expect(table).toBeVisible()
      await expect(page.locator('.el-table__empty-text').first()).toBeVisible()
    })
  })

  test.describe('API Integration', () => {
    test.use({ storageState: buildStorageState('THIRD_PARTY') })

    test('page calls blocks and transactions endpoints on load', async ({ page }) => {
      const requests = []
      page.on('request', (req) => {
        if (req.url().includes('/api/v1/blockchain')) {
          requests.push({ method: req.method(), url: req.url() })
        }
      })

      mockBlockchainApi(page)
      await loginViaToken(page, 'THIRD_PARTY')
      await page.goto('/enterprise/blockchain/browser')
      await page.waitForLoadState('networkidle')

      const blockReqs = requests.filter((r) => r.url.includes('/blocks/latest'))
      const txReqs = requests.filter((r) => r.url.includes('/transactions'))
      expect(blockReqs.length).toBeGreaterThanOrEqual(1)
      expect(txReqs.length).toBeGreaterThanOrEqual(1)
    })
  })
})
