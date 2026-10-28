import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { loginViaApi } from '../fixtures/auth'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const OUT_DIR = path.join(process.cwd(), 'test-results', 'ad-hoc-probes')

function ensureOutDir() {
  fs.mkdirSync(OUT_DIR, { recursive: true })
}

function writeJson(name: string, data: unknown) {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(data, null, 2), 'utf8')
}

test('probe user profile digital signature management ui', async ({ page }) => {
  ensureOutDir()
  const token = await loginViaApi(page, 'enterprise001', 'admin123')

  const keypairResponse = await page.request.get(`${API_BASE}/signature/keypair`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (keypairResponse.status() !== 200) {
    await page.request.post(`${API_BASE}/signature/keypair/generate`, {
      headers: { Authorization: `Bearer ${token}` },
    })
  }

  await page.goto(`${BASE_URL}/enterprise/user/profile`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)

  const signatureCard = page.locator('[data-testid="signature-card"]')
  await expect(signatureCard).toBeVisible()

  const statusText = ((await signatureCard.locator('[data-testid="signature-status"]').textContent()) || '').trim()
  const keyVersionText = ((await signatureCard.locator('[data-testid="signature-key-version"]').textContent()) || '').trim()
  const publicKeyPreview = await signatureCard.locator('[data-testid="signature-public-key"]').evaluate((node) => {
    const element = node as HTMLInputElement | HTMLTextAreaElement | HTMLElement
    return ('value' in element ? element.value : element.textContent || '').trim()
  })
  const buttonTexts = await signatureCard.locator('button').evaluateAll((buttons) =>
    buttons.map((button) => (button.textContent || '').trim()).filter(Boolean),
  )

  expect(statusText.length).toBeGreaterThan(0)
  expect(keyVersionText.length).toBeGreaterThan(0)
  expect(publicKeyPreview.length).toBeGreaterThan(40)

  await page.screenshot({
    path: path.join(OUT_DIR, 'user-profile-signature-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('user-profile-signature-probe-fixed-2026-05-24.json', {
    statusText,
    keyVersionText,
    publicKeyPreview,
    buttonTexts,
  })
})
