import { test, expect, type Page } from '@playwright/test'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(800)
}

async function waitForCaptchaImage(page: Page) {
  const captchaImage = page.locator('.captcha-image').first()
  await captchaImage.waitFor({ state: 'visible', timeout: 15000 })
  await expect(captchaImage).toHaveAttribute('src', /^data:image\/png;base64,/)
  return captchaImage
}

test('login page loads captcha image from backend', async ({ page }) => {
  await page.goto(`${BASE_URL}/login`)
  await settle(page)

  const captchaImage = await waitForCaptchaImage(page)
  const src = await captchaImage.getAttribute('src')

  expect(src).toMatch(/^data:image\/png;base64,/)
})

test('login page refreshes captcha image when clicked', async ({ page }) => {
  await page.goto(`${BASE_URL}/login`)
  await settle(page)

  const captchaImage = await waitForCaptchaImage(page)
  const before = await captchaImage.getAttribute('src')

  await captchaImage.click()
  await page.waitForFunction(
    (previous) => {
      const current = document.querySelector<HTMLImageElement>('.captcha-image')?.getAttribute('src')
      return Boolean(current && previous && current !== previous)
    },
    before,
    { timeout: 15000 },
  )

  const after = await captchaImage.getAttribute('src')
  expect(after).not.toBe(before)
  expect(after).toMatch(/^data:image\/png;base64,/)
})
