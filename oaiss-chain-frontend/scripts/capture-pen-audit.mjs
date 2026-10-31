#!/usr/bin/env node
/**
 * 逐页视觉取证：真实登录 → 逐路由截图 → 与 pen 参考图并排输出。
 *
 * 与旧 capture-ac2.mjs 的区别（旧脚本产出的证据不可信：01 与 03 字节完全相同）：
 *   - 每页截图后校验「落点路径与该页路由一致」，重定向冒充采集会直接报错
 *   - 每页截图后校验「相邻两页截图字节不同」，防止复用上一页画面
 *   - 验证码通过后端 Redis 读取（真实链路），不依赖环境变量手工输入
 *   - 截图前关闭引导弹窗、等待网络空闲与字体就绪
 *
 * 用法：node scripts/capture-pen-audit.mjs [--out <dir>] [--only <n>]
 */
import { chromium } from 'playwright'
import { mkdirSync, writeFileSync, readFileSync, existsSync, rmSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..')
const API = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const BASE = process.env.BASE_URL || 'http://localhost:5173'
const argOut = process.argv.indexOf('--out')
const OUT = argOut > -1 ? resolve(process.argv[argOut + 1]) : resolve(ROOT, '.pen-audit/screens')
const argOnly = process.argv.indexOf('--only')
const ONLY = argOnly > -1 ? process.argv[argOnly + 1] : null

const VIEWPORT = { width: 1440, height: 900 }

/** 与 pen 参考图一一对应的路由（编号对齐 pencli/png/） */
const PAGES = [
  ['01 碳核算填报', '/enterprise/carbon/upload', 'ENT'],
  ['02 官方网站首页', '/official-home', null],
  ['03 登录', '/login', null],
  ['04 排放数据', '/enterprise/emission/data', 'ENT'],
  ['05 碳中和项目', '/enterprise/carbon-neutral/projects', 'ENT'],
  ['06 项目详情', '/enterprise/carbon-neutral/projects/1', 'ENT'],
  ['07 碳核算公式', '/enterprise/carbon-formula', 'ENT'],
  ['08 数据可视化', '/enterprise/company/dashboard', 'ENT'],
  ['09 双向拍卖', '/enterprise/trading/market', 'ENT'],
  ['10 P2P交易', '/enterprise/trading/p2p', 'ENT'],
  ['11 订单管理', '/enterprise/orders/manage', 'ENT'],
  ['12 碳币账户', '/enterprise/carbon-coin/account', 'ENT'],
  ['13 信誉评分', '/enterprise/credit/score', 'ENT'],
  ['14 区块链浏览器', '/enterprise/blockchain/browser', 'ENT'],
  ['15 AI市场预测', '/enterprise/market-prediction', 'ENT'],
  ['16 AI企业推理', '/enterprise/enterprise-inference', 'ENT'],
  ['17 企业信息', '/enterprise/info', 'ENT'],
  ['18 个人中心', '/enterprise/user/profile', 'ENT'],
  ['19 待审报告', '/auditor/audit/list', 'REV'],
  ['20 项目核证', '/auditor/project/review', 'REV'],
  ['21 审核历史', '/auditor/review/history', 'REV'],
  ['22 上链补救', '/auditor/verify/list', 'REV'],
  ['23 监管面板', '/third-party/monitor', 'GOV'],
  ['24 用户管理', '/admin/system/users', 'ADM'],
  ['25 碳核算管理', '/admin/system/carbon', 'ADM'],
  ['26 系统配置', '/admin/system/config', 'ADM'],
  ['27 统计数据', '/admin/data/statistics', 'ADM'],
  ['28 证书管理', '/admin/certificates', 'ADM'],
]

const USERS = {
  ENT: 'enterprise001',
  REV: 'reviewer001',
  GOV: 'thirdparty001',
  ADM: 'admin',
}

function log(msg) {
  process.stdout.write(`${msg}\n`)
}

/** 验证码：直接读后端写入 Redis 的那一份，避免人工视觉输入 */
function redisCode(redisPassword, key) {
  const out = execFileSync('docker', [
    'exec', 'oaiss-redis', 'redis-cli', '-a', redisPassword, '--no-auth-warning', 'GET', `captcha:${key}`,
  ], { encoding: 'utf8' })
  return out.trim()
}

function redisPassword() {
  const args = JSON.parse(
    execFileSync('docker', ['inspect', 'oaiss-redis', '--format', '{{json .Args}}'], { encoding: 'utf8' }),
  )
  const i = args.indexOf('--requirepass')
  if (i < 0) throw new Error('redis 未设置 --requirepass，无法读取验证码')
  return args[i + 1]
}

/** 种子密码：从后端种子哈希反推会污染对话，改为由调用方通过环境变量或本文件旁配置提供 */
function seedPassword() {
  const p = process.env.TEST_USER_PASSWORD
  if (p) return p
  const local = resolve(ROOT, '.pen-audit', 'credentials.json')
  if (existsSync(local)) return JSON.parse(readFileSync(local, 'utf8')).password
  throw new Error(
    '缺少登录口令：请设置 TEST_USER_PASSWORD 环境变量，或写入 oaiss-chain-frontend/.pen-audit/credentials.json',
  )
}

async function login(request, username, password, rp) {
  const cap = await request.post(`${API}/auth/captcha`, { data: {} })
  const capBody = await cap.json()
  const captchaKey = capBody?.data?.captchaKey
  if (!captchaKey) throw new Error(`取验证码失败: ${JSON.stringify(capBody).slice(0, 200)}`)
  const captcha = redisCode(rp, captchaKey)
  const res = await request.post(`${API}/auth/login`, {
    data: { username, password, captchaKey, captcha },
  })
  const body = await res.json()
  const data = body?.data
  const token = data?.accessToken || data?.token || data?.access_token
  if (!token) throw new Error(`${username} 登录失败: ${JSON.stringify(body).slice(0, 300)}`)
  return { token, refreshToken: data?.refreshToken || '', expiresIn: data?.expiresIn || 7200 }
}

const sha = (buf) => createHash('sha256').update(buf).digest('hex').slice(0, 16)

async function main() {
  rmSync(OUT, { recursive: true, force: true })
  mkdirSync(OUT, { recursive: true })

  const { request } = await import('playwright')
  const apiCtx = await request.newContext()
  const rp = redisPassword()
  const password = seedPassword()

  const sessions = {}
  for (const [roleKey, username] of Object.entries(USERS)) {
    sessions[roleKey] = await login(apiCtx, username, password, rp)
    log(`✓ 登录 ${username}`)
  }
  await apiCtx.dispose()

  const browser = await chromium.launch()
  const report = []
  let prevHash = null

  for (const [name, path, roleKey] of PAGES) {
    if (ONLY && !name.startsWith(ONLY)) continue

    const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1 })
    if (roleKey) {
      const s = sessions[roleKey]
      await ctx.addInitScript(({ token, refresh, expiresIn }) => {
        localStorage.setItem('access_token', token)
        if (refresh) localStorage.setItem('refresh_token', refresh)
        localStorage.setItem('token_expiry', String(Date.now() + expiresIn * 1000))
        localStorage.setItem('remember_me', 'true')
        // 引导弹窗按用户隔离，预置已完成即可避免遮挡首屏
        document.addEventListener('DOMContentLoaded', () => {
          for (const k of Object.keys(localStorage)) {
            if (k.startsWith('oaiss_guided_tour_done')) localStorage.setItem(k, '1')
          }
        })
      }, { token: s.token, refresh: s.refreshToken, expiresIn: s.expiresIn })
    }

    const page = await ctx.newPage()
    const errors = []
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 200)) })
    page.on('pageerror', (e) => errors.push(`pageerror: ${String(e).slice(0, 200)}`))

    let landed = ''
    try {
      await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle', timeout: 45000 })
      landed = new URL(page.url()).pathname
      await page.waitForTimeout(1200)
      await page.evaluate(() => document.fonts?.ready)
      // 关掉可能存在的弹窗遮罩（新手引导/公告），再等一帧稳定
      await page.keyboard.press('Escape').catch(() => {})
      await page.waitForTimeout(400)
      const buf = await page.screenshot({ fullPage: false })
      const h = sha(buf)
      const file = resolve(OUT, `${name.replace(/\s+/g, '_')}.png`)
      writeFileSync(file, buf)

      const issues = []
      if (landed !== path) issues.push(`落点不符: 期望 ${path} 实际 ${landed}`)
      if (prevHash && h === prevHash) issues.push('与上一页截图字节相同（疑似复用画面）')
      if (errors.length) issues.push(`控制台错误 ${errors.length} 条: ${errors[0]}`)
      prevHash = h

      report.push({ name, path, landed, file, hash: h, issues })
      log(`${issues.length ? '✗' : '✓'} ${name} ${landed} ${h}${issues.length ? '  ' + issues.join(' | ') : ''}`)
    } catch (e) {
      report.push({ name, path, landed, issues: [`采集失败: ${String(e).slice(0, 200)}`] })
      log(`✗ ${name} 采集失败: ${String(e).slice(0, 160)}`)
    } finally {
      await ctx.close()
    }
  }

  await browser.close()
  writeFileSync(resolve(OUT, '..', 'capture-report.json'), JSON.stringify(report, null, 2))
  const bad = report.filter((r) => r.issues?.length)
  log(`\n共 ${report.length} 页，异常 ${bad.length} 页`)
  process.exit(bad.length ? 1 : 0)
}

main().catch((e) => {
  console.error(e.message || e)
  process.exit(1)
})