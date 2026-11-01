import { test, expect } from '@playwright/test'
import { loginViaApi } from '../fixtures/auth'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const API = `${BASE_URL}/api/v1`

test('管理端用户生命周期链路：新增→新账号登录→keyword定位→删除→被拒登录', async ({ page }) => {
  test.setTimeout(90000)
  const stamp = Date.now().toString().slice(-8)
  const uname = `ui_${stamp}`

  await loginViaApi(page, 'admin', 'admin123')
  console.log('STEP create-start')
  await page.goto(`${BASE_URL}/admin/system/users`)
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1000)

  const dialog = page.locator('.el-dialog').filter({ hasText: '新增用户' })

  // 1. 打开创建弹窗
  await page.getByRole('button', { name: '新增用户' }).click()
  await expect(dialog).toBeVisible()

  // 2. 填表（默认企业类型，密码两处）
  await dialog.locator('input').nth(0).fill(uname)
  await dialog.locator('input[type=password]').nth(0).fill('admin123')
  await dialog.locator('input[type=password]').nth(1).fill('admin123')
  await dialog.getByRole('button', { name: '确认' }).click()

  // 3. 成功提示 + 弹窗关闭（toast 为 3s 闪现元素，用 .first() 且不依赖删除段）
  await expect(page.locator('.el-message--success').first()).toBeVisible({ timeout: 6000 })
  await expect(dialog).toBeHidden({ timeout: 6000 })

  // 4. 新账号 API 登录成功（创建真实生效）
  const r1 = await page.request.post(`${API}/auth/login`, {
    data: { username: uname, password: 'admin123' },
  })
  expect(r1.status()).toBe(200)

  // 5. keyword 定位新用户行
  await page.getByPlaceholder('用户名关键词').fill(uname)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.waitForTimeout(600)
  const row = page.locator('.el-table__row').filter({ hasText: uname })
  await expect(row).toBeVisible()

  // 6. 行内删除 → 确认
  const delRespP = page.waitForResponse((r) => r.url().includes('/admin/users/') && r.request().method() === 'DELETE', { timeout: 8000 }).catch(() => null)
  await row.getByRole('button', { name: '删除' }).click()
  const box = page.locator('.el-message-box')
  await expect(box).toBeVisible()
  await box.getByRole('button', { name: '确认' }).click()
  const delResp = await delRespP
  expect(delResp?.status()).toBe(200)
  await expect(box).toBeHidden({ timeout: 6000 })
  // 删除成功以 DELETE 200 + 名册刷新为准；toast 为 3s 闪现元素不作断言（此前误判根因）
  await expect(page.locator('.el-table__row').filter({ hasText: uname })).toHaveCount(0, { timeout: 8000 })

  // 7. 已删账号登录被拒（下线闸门）
  const r2 = await page.request.post(`${API}/auth/login`, { data: { username: uname, password: 'admin123' } })
  expect(r2.status()).toBe(401)

  // 8. 名册中消失
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.waitForTimeout(600)
  await expect(page.locator('.el-table__row').filter({ hasText: uname })).toHaveCount(0)
})
