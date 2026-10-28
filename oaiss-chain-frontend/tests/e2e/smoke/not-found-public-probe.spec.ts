import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const OUT_DIR = path.join(process.cwd(), 'test-results', 'ad-hoc-probes')

function ensureOutDir() {
  fs.mkdirSync(OUT_DIR, { recursive: true })
}

function writeJson(name: string, data: unknown) {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(data, null, 2), 'utf8')
}

test('probe public 404 route is not auth guarded', async ({ page }) => {
  ensureOutDir()
  await page.goto(`${BASE_URL}/nonexistent-path`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(600)

  await expect(page).toHaveURL(/\/nonexistent-path$/)
  const visible404 = await page.getByText('404').isVisible()
  const bodyText = ((await page.locator('body').textContent()) || '').trim()

  expect(visible404).toBe(true)
  expect(bodyText).not.toContain('登录')

  await page.screenshot({
    path: path.join(OUT_DIR, 'not-found-public-fixed-2026-05-24.png'),
    fullPage: true,
  })

  writeJson('not-found-public-probe-fixed-2026-05-24.json', {
    url: page.url(),
    visible404,
    bodyTextSnippet: bodyText.slice(0, 120),
  })
})
