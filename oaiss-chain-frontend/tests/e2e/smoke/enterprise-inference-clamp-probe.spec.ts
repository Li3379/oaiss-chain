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

test('probe enterprise inference clamps negative anomaly scores', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')

  await page.route(/\/api\/v1\/predict\/enterprise\/\d+\/inference(?:\?.*)?$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: {
          enterpriseId: 1,
          complianceStatus: 'at_risk',
          confidence: 0.82,
          anomalyScore: -0.18,
          isAnomaly: true,
          riskFactors: ['Mock factor'],
          modelVersion: 'mock-negative-score',
        },
      }),
    })
  })

  const pageErrors: string[] = []
  page.on('pageerror', (error) => {
    pageErrors.push(error.message)
  })

  await page.goto(`${BASE_URL}/enterprise/enterprise-inference`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const dashboards = page.locator('.el-progress')
  const dashboardCount = await dashboards.count()
  const pageText = (await page.locator('body').textContent()) || ''
  const statusText = ((await page.locator('.stat-card .el-tag').first().textContent()) || '').trim()
  const anomalyText = ((await page.locator('.score-value').textContent()) || '').trim()

  await expect(dashboards).toHaveCount(1)
  expect(pageErrors).toEqual([])
  expect(statusText).not.toBe('at_risk')
  expect(anomalyText).toBe('-0.180')
  expect(pageText.includes('-18%')).toBe(false)

  await page.screenshot({
    path: path.join(OUT_DIR, 'enterprise-inference-clamp-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('enterprise-inference-clamp-probe-fixed-2026-05-24.json', {
    dashboardCount,
    statusText,
    anomalyText,
    pageErrors,
    containsNegativePercent: pageText.includes('-18%'),
  })
})
