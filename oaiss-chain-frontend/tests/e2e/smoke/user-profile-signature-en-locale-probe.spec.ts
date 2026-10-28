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

test('probe user profile signature status text follows en-US locale', async ({ page }) => {
  ensureOutDir()
  await loginViaApi(page, 'enterprise001', 'admin123')

  await page.evaluate(() => {
    localStorage.setItem('locale', 'en-US')
  })

  await page.goto(`${BASE_URL}/enterprise/user/profile`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const signatureCard = page.locator('[data-testid="signature-card"]')
  await expect(signatureCard).toBeVisible()

  const statusText = ((await signatureCard.locator('[data-testid="signature-status"]').textContent()) || '').trim()
  const statusLower = statusText.toLowerCase()

  const englishStatusKeywords = ['active', 'revoked', 'expired', 'keypair ready', 'keypair not generated']
  const containsEnglishStatus = englishStatusKeywords.some((keyword) => statusLower.includes(keyword))

  writeJson('user-profile-signature-en-locale-probe-2026-05-26.json', {
    statusText,
    containsEnglishStatus,
  })

  await page.screenshot({
    path: path.join(OUT_DIR, 'user-profile-signature-en-locale-probe-2026-05-26.png'),
    fullPage: true,
  })

  expect(containsEnglishStatus).toBe(true)
})
