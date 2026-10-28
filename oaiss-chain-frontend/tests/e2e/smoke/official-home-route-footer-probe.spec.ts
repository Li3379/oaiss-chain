import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { loginViaApi } from '../fixtures/auth'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const OUT_DIR = path.join(process.cwd(), 'test-results', 'ad-hoc-probes')

function ensureOutDir() {
  fs.mkdirSync(OUT_DIR, { recursive: true })
}

function writeJson(name: string, data: unknown) {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(data, null, 2), 'utf8')
}

test('probe official home routes and footer anchor scrolling', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')
  await page.goto(`${BASE_URL}/official-home`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(800)

  const featureButtons = page.locator('.feature-card .el-button')
  const roleButtons = page.locator('.role-card .el-button')

  await expect(featureButtons.nth(1)).toHaveAttribute('data-route', '/enterprise/blockchain/browser')
  await featureButtons.nth(1).click()
  await expect(page).toHaveURL(/\/enterprise\/blockchain\/browser$/)
  await page.goBack()
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(400)

  await expect(featureButtons.nth(3)).toHaveAttribute('data-route', '/enterprise/user/profile')
  await featureButtons.nth(3).click()
  await expect(page).toHaveURL(/\/enterprise\/user\/profile$/)
  await page.goBack()
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(400)

  await expect(roleButtons.nth(3)).toHaveAttribute('data-route', '/third-party/monitor')

  const footerMore = page.locator('footer .footer-columns > div').first()
  const beforeScroll = await page.evaluate(() => window.scrollY)
  await footerMore.getByRole('button').nth(1).click()
  await page.waitForTimeout(500)
  const scrollAfterFooterClick = await page.evaluate(() => window.scrollY)
  const researchTop = await page.locator('#research').evaluate((el) => {
    const rect = el.getBoundingClientRect()
    return rect.top
  })

  expect(scrollAfterFooterClick).toBeGreaterThan(beforeScroll)
  expect(Math.abs(researchTop)).toBeLessThan(120)

  await page.screenshot({
    path: path.join(OUT_DIR, 'official-home-route-footer-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('official-home-route-footer-probe-fixed-2026-05-24.json', {
    url: page.url(),
    scrollAfterFooterClick,
    researchTop,
  })
})
