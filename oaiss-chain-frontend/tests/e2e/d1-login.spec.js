import { test, expect } from '@playwright/test'

test.describe('D1: 登录页', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
  })

  test('登录页渲染完整', async ({ page }) => {
    // pen 稿：页面的 h1 是登录卡标题，左栏叙事是 h2
    await expect(page.locator('h1')).toContainText('登录')
    await expect(page.locator('.brand-headline')).toBeVisible()
    await expect(page.getByPlaceholder('请输入账号')).toBeVisible()
    await expect(page.getByPlaceholder('请输入密码')).toBeVisible()
    await expect(page.getByPlaceholder('请输入验证码')).toBeVisible()
    await expect(page.getByRole('button', { name: '进入系统' })).toBeVisible()
  })

  test('验证码图片可加载', async ({ page }) => {
    const captchaImg = page.locator('img[alt="验证码"]')
    await expect(captchaImg).toBeVisible()
  })

  test('左栏实拍配图已加载', async ({ page }) => {
    const art = page.locator('.brand-art img')
    await expect(art).toBeVisible()
    expect(await art.evaluate((img) => img.complete && img.naturalWidth > 0)).toBe(true)
  })

  test('记住账号复选框默认选中', async ({ page }) => {
    const checkbox = page.getByRole('checkbox', { name: '记住账号' })
    await expect(checkbox).toBeChecked()
  })

  test('未登录访问受保护页面应重定向到登录页', async ({ page }) => {
    await page.goto('/enterprise/carbon/upload')
    await expect(page).toHaveURL(/\/login/)
  })
})
