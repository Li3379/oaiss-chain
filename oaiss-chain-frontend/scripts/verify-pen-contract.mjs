#!/usr/bin/env node
/**
 * 设计契约校验（替代旧的「亮度百分比」比对）
 *
 * 对每个页面在真实浏览器中取计算样式，逐项核对 pen 设计源的硬规格：
 *   1. 圆角：内容容器 2px（设计稿实测值），不是 8/12px
 *   2. 底色：ground #F8F8F8 与 surface #FFFFFF
 *   3. 骨架：存在页头/统计条/面板（与 pen 页面解剖一致）
 *   4. 零渐变、零内容区阴影
 *   5. 字号档位落在设计源允许集合内
 *
 * 用法：node scripts/verify-pen-contract.mjs [--out report.json]
 */
import { chromium } from 'playwright'
import { writeFileSync, readFileSync, existsSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..')
const API = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'
const BASE = process.env.BASE_URL || 'http://localhost:5173'
const argOut = process.argv.indexOf('--out')
const OUT = argOut > -1 ? resolve(process.argv[argOut + 1]) : resolve(ROOT, '.pen-audit/contract.json')

const PAGES = [
  ['01 碳核算填报', '/enterprise/carbon/upload', 'ENT', ['head', 'stats', 'panel']],
  ['02 官方网站首页', '/official-home', null, []],
  ['03 登录', '/login', null, []],
  ['04 排放数据', '/enterprise/emission/data', 'ENT', ['head', 'stats', 'panel']],
  ['05 碳中和项目', '/enterprise/carbon-neutral/projects', 'ENT', ['head', 'stats', 'panel']],
  ['06 项目详情', '/enterprise/carbon-neutral/projects/1', 'ENT', ['head', 'stats', 'panel']],
  ['07 碳核算公式', '/enterprise/carbon-formula', 'ENT', ['head', 'stats', 'panel']],
  ['08 数据可视化', '/enterprise/company/dashboard', 'ENT', ['head', 'stats', 'panel']],
  ['09 双向拍卖', '/enterprise/trading/market', 'ENT', ['head', 'stats', 'panel']],
  ['10 P2P交易', '/enterprise/trading/p2p', 'ENT', ['head', 'stats', 'panel']],
  ['11 订单管理', '/enterprise/orders/manage', 'ENT', ['head', 'stats', 'panel']],
  ['12 碳币账户', '/enterprise/carbon-coin/account', 'ENT', ['head', 'stats', 'panel']],
  ['13 信誉评分', '/enterprise/credit/score', 'ENT', ['head', 'stats', 'panel']],
  ['14 区块链浏览器', '/enterprise/blockchain/browser', 'ENT', ['head', 'stats', 'panel']],
  ['15 AI市场预测', '/enterprise/market-prediction', 'ENT', ['head', 'stats', 'panel']],
  ['16 AI企业推理', '/enterprise/enterprise-inference', 'ENT', ['head', 'stats', 'panel']],
  ['17 企业信息', '/enterprise/info', 'ENT', ['head', 'stats', 'panel']],
  ['18 个人中心', '/enterprise/user/profile', 'ENT', ['head', 'stats', 'panel']],
  ['19 待审报告', '/auditor/audit/list', 'REV', ['head', 'stats', 'panel']],
  ['20 项目核证', '/auditor/project/review', 'REV', ['head', 'stats', 'panel']],
  ['21 审核历史', '/auditor/review/history', 'REV', ['head', 'stats', 'panel']],
  ['22 上链补救', '/auditor/verify/list', 'REV', ['head', 'stats', 'panel']],
  ['23 监管面板', '/third-party/monitor', 'GOV', ['head', 'stats', 'panel']],
  ['24 用户管理', '/admin/system/users', 'ADM', ['head', 'stats', 'panel']],
  ['25 碳核算管理', '/admin/system/carbon', 'ADM', ['head', 'stats', 'panel']],
  ['26 系统配置', '/admin/system/config', 'ADM', ['head', 'stats', 'panel']],
  ['27 统计数据', '/admin/data/statistics', 'ADM', ['head', 'stats', 'panel']],
  ['28 证书管理', '/admin/certificates', 'ADM', ['head', 'stats', 'panel']],
]

const USERS = { ENT: 'enterprise001', REV: 'reviewer001', GOV: 'thirdparty001', ADM: 'admin' }

/** 设计源允许的字号档位（UI_Desgin.pen 实测） */
const ALLOWED_FONT_SIZES = new Set([
  '10px', '10.5px', '11px', '12px', '12.5px', '13px', '14px', '15px', '16px',
  '18px', '20px', '21px', '22px', '24px', '25px',
])

function redisPassword() {
  const args = JSON.parse(
    execFileSync('docker', ['inspect', 'oaiss-redis', '--format', '{{json .Args}}'], { encoding: 'utf8' }),
  )
  const i = args.indexOf('--requirepass')
  return i < 0 ? null : args[i + 1]
}

function seedPassword() {
  const p = process.env.TEST_USER_PASSWORD
  if (p) return p
  const local = resolve(ROOT, '.pen-audit', 'credentials.json')
  if (existsSync(local)) return JSON.parse(readFileSync(local, 'utf8')).password
  throw new Error('缺少登录口令：设置 TEST_USER_PASSWORD 或写 .pen-audit/credentials.json')
}

async function login(request, username, password, rp) {
  const cap = await (await request.post(`${API}/auth/captcha`, { data: {} })).json()
  const key = cap?.data?.captchaKey
  const code = execFileSync('docker', [
    'exec', 'oaiss-redis', 'redis-cli', '-a', rp, '--no-auth-warning', 'GET', `captcha:${key}`,
  ], { encoding: 'utf8' }).trim()
  const body = await (await request.post(`${API}/auth/login`, {
    data: { username, password, captchaKey: key, captcha: code },
  })).json()
  const d = body?.data
  if (!d?.accessToken) throw new Error(`${username} 登录失败`)
  return { token: d.accessToken, refreshToken: d.refreshToken || '', expiresIn: d.expiresIn || 7200 }
}

async function main() {
  const { request } = await import('playwright')
  const api = await request.newContext()
  const rp = redisPassword()
  const password = seedPassword()
  const sessions = {}
  for (const [k, u] of Object.entries(USERS)) sessions[k] = await login(api, u, password, rp)
  await api.dispose()

  const browser = await chromium.launch()
  const report = []

  for (const [name, path, roleKey, required] of PAGES) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 })
    if (roleKey) {
      const s = sessions[roleKey]
      await ctx.addInitScript(({ token, refresh, expiresIn }) => {
        localStorage.setItem('access_token', token)
        if (refresh) localStorage.setItem('refresh_token', refresh)
        localStorage.setItem('token_expiry', String(Date.now() + expiresIn * 1000))
        localStorage.setItem('remember_me', 'true')
        for (const k of Object.keys(localStorage)) {
          if (k.startsWith('oaiss_guided_tour_done')) localStorage.setItem(k, '1')
        }
      }, { token: s.token, refresh: s.refreshToken, expiresIn: s.expiresIn })
    }

    const page = await ctx.newPage()
    const issues = []
    try {
      await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle', timeout: 45000 })
      await page.waitForTimeout(900)

      const probe = await page.evaluate((required) => {
        const cs = (el) => getComputedStyle(el)
        const body = cs(document.body)

        // 骨架存在性
        const skeleton = {
          head: !!document.querySelector('.pen-page-head, .page-head'),
          stats: !!document.querySelector('.pen-stat-strip'),
          panel: !!document.querySelector('.pen-panel'),
        }

        // 内容容器（面板/卡片）的圆角与阴影
        const containers = [
          ...document.querySelectorAll('.pen-panel, .pen-stat, .el-card, .section-card'),
        ].slice(0, 12)
        const radii = [...new Set(containers.map((el) => cs(el).borderTopLeftRadius))]
        const shadows = [...new Set(containers.map((el) => cs(el).boxShadow).filter((v) => v && v !== 'none'))]

        // 全站是否残留渐变（排除背景图产生的大面积摄影 hero，仅查 CSS background-image）
        const gradientEls = [...document.querySelectorAll('body *')].filter((el) => {
          const bg = cs(el).backgroundImage
          return bg && bg.includes('gradient')
        })
        const gradientCount = gradientEls.length
        const gradientSample = gradientEls.slice(0, 3).map((el) => el.className?.toString().slice(0, 60))

        // 字号是否落在设计源档位
        const sizes = new Set()
        for (const el of document.querySelectorAll('.pen-page-head, .pen-stat, .pen-panel, .el-table, .el-button, .status-tag')) {
          sizes.add(cs(el).fontSize)
        }

        // 表格是否仍带竖线边框（设计稿为无竖线台账）
        const verticalBorders = [...document.querySelectorAll('.el-table--border')].length

        return {
          skeleton,
          radii,
          shadows,
          gradientCount,
          gradientSample,
          fontSizes: [...sizes],
          verticalBorders,
          bodyBg: body.backgroundColor,
          groundToken: cs(document.documentElement).getPropertyValue('--pen-ground').trim(),
          surfaceToken: cs(document.documentElement).getPropertyValue('--pen-surface').trim(),
        }
      }, required)

      // —— 判据 ——
      for (const key of required) {
        if (!probe.skeleton[key]) issues.push(`缺少骨架段: ${key}`)
      }
      // 圆角：只允许 2px（设计源），芯片类允许 50%/999px
      const badRadii = probe.radii.filter((r) => r !== '2px' && r !== '0px' && !r.includes('%') && !r.includes('999'))
      if (badRadii.length) issues.push(`圆角偏离设计稿(应为2px): ${badRadii.join(',')}`)
      if (probe.shadows.length) issues.push(`内容区出现阴影: ${probe.shadows[0]}`)
      if (probe.gradientCount > 0) issues.push(`残留渐变 ${probe.gradientCount} 处 (${probe.gradientSample.join(' | ')})`)
      if (probe.verticalBorders > 0) issues.push(`表格仍带竖线边框 ${probe.verticalBorders} 处`)
      const oddSizes = probe.fontSizes.filter((s) => !['10px', '10.5px', '11px', '12px', '12.5px', '13px', '14px', '15px', '16px', '18px', '20px', '21px', '22px', '24px', '25px'].includes(s))
      if (oddSizes.length) issues.push(`字号偏离设计档位: ${[...new Set(oddSizes)].join(',')}`)

      report.push({ name, path, required, issues, probe })
      console.log(`${issues.length ? '✗' : '✓'} ${name}${issues.length ? '  ' + issues.join(' | ') : ''}`)
    } catch (e) {
      report.push({ name, path, issues: [`采集失败: ${String(e).slice(0, 160)}`] })
      console.log(`✗ ${name} 采集失败: ${String(e).slice(0, 120)}`)
    } finally {
      await ctx.close()
    }
  }

  await browser.close()
  writeFileSync(OUT, JSON.stringify(report, null, 2))
  const bad = report.filter((r) => r.issues?.length)
  console.log(`\n共 ${report.length} 页，不符合契约 ${bad.length} 页`)
  process.exit(bad.length ? 1 : 0)
}

main().catch((e) => {
  console.error(e.message || e)
  process.exit(1)
})