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

test('probe carbon formula enterprise-name prefill source', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')
  await page.goto(`${BASE_URL}/enterprise/carbon-formula`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const powerGenerationName = await page.locator('input[placeholder*="请输入企业名称"]').first().inputValue()
  await page.getByRole('tab', { name: /电网|Grid/i }).click().catch(() => {})
  await page.waitForTimeout(400)
  const powerGridName = await page.locator('input[placeholder*="请输入企业名称"]').last().inputValue()

  expect(powerGenerationName).toBeTruthy()
  expect(powerGenerationName).toEqual(powerGridName)
  expect(powerGenerationName).not.toContain('@')

  await page.screenshot({
    path: path.join(OUT_DIR, 'carbon-formula-prefill-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('carbon-formula-prefill-probe-fixed-2026-05-24.json', {
    powerGenerationName,
    powerGridName,
  })
})
