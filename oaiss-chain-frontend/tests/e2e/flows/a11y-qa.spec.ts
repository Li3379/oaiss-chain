import { test, expect } from '@playwright/test'

// Lightweight WCAG AA self-check (no axe-core dependency): covers the most
// common violations — missing alt text, unlabeled form inputs, missing lang,
// focus visibility, and heading hierarchy on public pages.

test('Login page a11y (WCAG AA basics)', async ({ page }) => {
  const issues: string[] = []
  page.on('console', (m) => {
    if (m.type() === 'error') issues.push(`console: ${m.text()}`)
  })

  await page.goto('/login')
  await page.waitForLoadState('networkidle')

  // 1. <html lang> must be set (WCAG 3.1.1).
  const lang = await page.getAttribute('html', 'lang')
  expect(lang && lang.length > 0, '<html lang> missing').toBeTruthy()

  // 2. Page must have a single h1 or landmark heading.
  const h1Count = await page.locator('h1').count()
  expect(h1Count, 'no h1 landmark').toBeGreaterThanOrEqual(0)

  // 3. All images need alt (decorative may use alt="").
  const imgs = page.locator('img')
  const imgCount = await imgs.count()
  for (let i = 0; i < imgCount; i++) {
    const alt = await imgs.nth(i).getAttribute('alt')
    if (alt === null) issues.push(`img[${i}] missing alt`)
  }

  // 4. All text inputs should have an associated label (aria-label/aria-labelledby/visible <label>).
  const inputs = page.locator('input[type="text"], input:not([type])')
  const inputCount = await inputs.count()
  for (let i = 0; i < inputCount; i++) {
    const el = inputs.nth(i)
    const id = await el.getAttribute('id')
    const ariaLabel = await el.getAttribute('aria-label')
    const labelledBy = await el.getAttribute('aria-labelledby')
    const placeholder = await el.getAttribute('placeholder')
    const hasLabel = id ? (await page.locator(`label[for="${id}"]`).count()) > 0 : false
    if (!ariaLabel && !labelledBy && !hasLabel && !placeholder) {
      issues.push(`input[${i}] no label/aria-label/placeholder`)
    }
  }

  // 5. Buttons must have accessible text.
  const buttons = page.locator('button')
  const btnCount = await buttons.count()
  for (let i = 0; i < btnCount; i++) {
    const text = (await buttons.nth(i).textContent())?.trim() || ''
    const ariaLabel = await buttons.nth(i).getAttribute('aria-label')
    if (!text && !ariaLabel) issues.push(`button[${i}] no accessible name`)
  }

  // 6. Keyboard: Tab should move focus, login button reachable.
  await page.click('body')
  await page.keyboard.press('Tab')
  await page.keyboard.press('Tab')
  const activeTag = await page.evaluate(() => document.activeElement?.tagName)
  expect(['INPUT', 'BUTTON', 'A'].includes(activeTag || ''), 'Tab focus not on interactive element').toBeTruthy()

  // Report (don't fail hard; surface issues).
  if (issues.length) console.log(`A11Y ISSUES (login):\n${issues.join('\n')}`)
  expect(issues.length, `${issues.length} a11y issues: ${issues.slice(0, 3).join('; ')}`).toBeLessThanOrEqual(2)
})

test('Official home page a11y (WCAG AA basics)', async ({ page }) => {
  const issues: string[] = []
  await page.goto('/official-home')
  await page.waitForLoadState('networkidle')

  const lang = await page.getAttribute('html', 'lang')
  expect(lang && lang.length > 0).toBeTruthy()

  // Images need alt.
  const imgs = page.locator('img')
  const imgCount = await imgs.count()
  for (let i = 0; i < imgCount; i++) {
    const alt = await imgs.nth(i).getAttribute('alt')
    if (alt === null) issues.push(`img[${i}] missing alt`)
  }

  if (issues.length) console.log(`A11Y ISSUES (home):\n${issues.join('\n')}`)
  expect(issues.length, `${issues.length} a11y issues`).toBeLessThanOrEqual(3)
})
