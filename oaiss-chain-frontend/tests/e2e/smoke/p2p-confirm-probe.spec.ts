import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { loginViaToken } from '../fixtures/auth'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const OUT_DIR = path.join(process.cwd(), 'test-results', 'ad-hoc-probes')

function ensureOutDir() {
  fs.mkdirSync(OUT_DIR, { recursive: true })
}

function writeJson(name: string, data: unknown) {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(data, null, 2), 'utf8')
}

test('probe p2p pending trade exposes confirm action', async ({ page }) => {
  ensureOutDir()
  await loginViaToken(page, 'ENTERPRISE')

  let confirmedTradeId: number | null = null

  await page.route('**/api/v1/trade/my-trades**', async (route) => {
    const request = route.request()
    const confirmed = confirmedTradeId !== null
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: {
          content: [
            {
              id: 701,
              tradeNo: 'P2P-701',
              buyerName: 'Enterprise Buyer',
              sellerName: 'Enterprise Seller',
              quantity: 12,
              unitPrice: 88,
              totalAmount: 1056,
              status: confirmed ? 2 : 0,
              statusText: confirmed ? '已完成' : '待处理',
              createdAt: '2026-05-24T10:00:00',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          size: 10,
          number: 0,
          first: true,
          last: true,
          empty: false,
        },
      }),
    })
  })

  await page.route('**/api/v1/trade/701/confirm', async (route) => {
    confirmedTradeId = 701
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: { id: 701, status: 2, statusText: '已完成' },
      }),
    })
  })

  await page.goto(`${BASE_URL}/enterprise/trading/p2p`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1000)

  const actionCell = page.locator('table tbody tr').first()
  const confirmButton = actionCell.getByRole('button', { name: /Confirm|确认/ })
  const cancelButton = actionCell.getByRole('button', { name: /Cancel|取消/ })

  await expect(confirmButton).toBeVisible()
  await expect(cancelButton).toBeVisible()

  await confirmButton.click()
  await page.getByRole('button', { name: /Confirm|确认/ }).last().click()
  await page.waitForTimeout(800)

  const actionTextsAfter = await actionCell.locator('button').allTextContents()

  await page.screenshot({
    path: path.join(OUT_DIR, 'p2p-confirm-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('p2p-confirm-probe-fixed-2026-05-24.json', {
    confirmedTradeId,
    actionTextsAfter,
  })
})
