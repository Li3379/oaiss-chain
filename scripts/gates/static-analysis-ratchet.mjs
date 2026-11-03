#!/usr/bin/env node
/**
 * 静态分析棘轮门禁（PMD / SpotBugs）。
 *
 * 背景：`mvn pmd:check -q || true` —— 这条命令永远不会失败。
 * `failOnViolation=false`（pom）让 mvn 本身就返回 0，再叠一个 `|| true`，
 * 等于把静态分析整个删除，却还在 CI 页面上显示一个绿色的 "Java (PMD + SpotBugs)"。
 * 这比不接 CI 更糟：它给出的是「已检查」的错误安全感。
 *
 * 为什么不直接打开 failOnViolation：
 *   仓库存量有数百条告警（绝大多数是 GuardLogStatement 这类风格项）。
 *   直接硬卡会让所有人的 PR 立刻堵死，团队的第一反应一定是再加一个 `|| true`，
 *   于是回到原点，还多了一层「我们试过了」的错觉。
 *
 * 棘轮（ratchet）语义：
 *   - 存量告警数写入基线文件，**不允许变多**
 *   - 修掉多少就自动下调基线（本次运行值 < 基线时提示收紧）
 *   - 新增代码引入新告警会立刻超基线 → 红
 *
 * 这样存量不阻塞，增量不劣化，方向单向下行。
 *
 * 用法：
 *   node scripts/gates/static-analysis-ratchet.mjs            # 校验
 *   node scripts/gates/static-analysis-ratchet.mjs --update   # 按当前值收紧基线
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..', '..')
const BACKEND = join(ROOT, 'oaiss-chain-backend')
const BASELINE_PATH = join(__dirname, 'static-analysis-baseline.json')

const UPDATE = process.argv.includes('--update')

function fail(msg) {
  console.error(`✗ ${msg}`)
  process.exit(1)
}

function readIfExists(p) {
  return existsSync(p) ? readFileSync(p, 'utf8') : null
}

/**
 * PMD 报告：统计 <violation> 数量，并按规则聚合，便于一眼看出新增项来自哪条规则。
 * 不解析完整 XML —— 只需要计数与规则分布，正则足够且不受 DTD 影响。
 *
 * 注意引号：PMD 吐双引号，SpotBugs 吐单引号。两个都写死会在换版本时静默归零
 * （表现为「告警数突然变成 0」，看起来像全修好了）。统一用 ['"] 兼容。
 */
const ATTR = (name) => new RegExp(`\\b${name}=['"]([^'"]+)['"]`, 'g')

/** 本地时区的 YYYY-MM-DD（不用 toISOString，它是 UTC）。 */
function localDate() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function parsePmd(xml) {
  const total = (xml.match(/<violation\b/g) || []).length
  const byRule = {}
  for (const m of xml.matchAll(ATTR('rule'))) {
    byRule[m[1]] = (byRule[m[1]] || 0) + 1
  }
  return { total, byRule }
}

/**
 * SpotBugs 报告：<BugInstance> 数量。
 * priority: 1=High 2=Medium 3=Low —— 只把 High 计入硬门槛，
 * 因为 Low 里混着大量 BL_ 类误报，卡它只会逼人加豁免清单。
 */
function parseSpotbugs(xml) {
  const total = (xml.match(/<BugInstance\b/g) || []).length
  const high = (xml.match(/<BugInstance\b[^>]*\bpriority=['"]1['"]/g) || []).length
  const byType = {}
  for (const m of xml.matchAll(ATTR('type'))) {
    byType[m[1]] = (byType[m[1]] || 0) + 1
  }
  return { total, high, byType }
}

const pmdXml = readIfExists(join(BACKEND, 'target', 'pmd.xml'))
const sbXml = readIfExists(join(BACKEND, 'target', 'spotbugsXml.xml'))

if (!pmdXml && !sbXml) {
  fail(
    '未找到 PMD/SpotBugs 报告。请先运行：\n' +
      '  cd oaiss-chain-backend\n' +
      '  mvn -B pmd:pmd compile spotbugs:spotbugs',
  )
}

const current = {}
if (pmdXml) {
  const pmd = parsePmd(pmdXml)
  current.pmd = pmd.total
  current.pmdByRule = pmd.byRule
  console.log(`PMD 告警: ${pmd.total}`)
} else {
  console.log('PMD 告警: (无报告，跳过)')
}

if (sbXml) {
  const sb = parseSpotbugs(sbXml)
  current.spotbugs = sb.total
  current.spotbugsHigh = sb.high
  current.spotbugsByType = sb.byType
  console.log(`SpotBugs: 共 ${sb.total}，其中 High ${sb.high}`)
} else {
  console.log('SpotBugs: (无报告，跳过)')
}

if (UPDATE) {
  const baseline = existsSync(BASELINE_PATH)
    ? JSON.parse(readFileSync(BASELINE_PATH, 'utf8'))
    : {}
  const next = {
    ...baseline,
    pmd: current.pmd ?? baseline.pmd ?? 0,
    spotbugs: current.spotbugs ?? baseline.spotbugs ?? 0,
    spotbugsHigh: current.spotbugsHigh ?? baseline.spotbugsHigh ?? 0,
    // 用本地日期而非 UTC：toISOString() 在 UTC+8 的凌晨会把日期算成前一天，
    // 基线里的 updatedAt 因此整体偏一天，读的人会误判「昨天才收紧过」。
    updatedAt: localDate(),
  }
  writeFileSync(BASELINE_PATH, `${JSON.stringify(next, null, 2)}\n`, 'utf8')
  console.log(`\n已收紧基线 → ${BASELINE_PATH}`)
  console.log(JSON.stringify(next, null, 2))
  process.exit(0)
}

if (!existsSync(BASELINE_PATH)) {
  fail(
    `缺少基线文件 ${BASELINE_PATH}。\n` +
      '首次接入请运行：node scripts/gates/static-analysis-ratchet.mjs --update',
  )
}

const baseline = JSON.parse(readFileSync(BASELINE_PATH, 'utf8'))
const problems = []

function compare(label, cur, base) {
  if (cur === undefined || base === undefined) return
  if (cur > base) {
    problems.push(`${label} 增加：${base} → ${cur}（新增 ${cur - base} 条）`)
  } else if (cur < base) {
    console.log(`↓ ${label} 下降：${base} → ${cur}（可运行 --update 收紧基线）`)
  } else {
    console.log(`= ${label} 持平：${cur}`)
  }
}

compare('PMD 告警', current.pmd, baseline.pmd)
compare('SpotBugs 总数', current.spotbugs, baseline.spotbugs)
compare('SpotBugs High', current.spotbugsHigh, baseline.spotbugsHigh)

if (problems.length > 0) {
  console.error('\n✗ 静态分析质量出现劣化（棘轮只允许下降，不允许多于基线）：')
  for (const p of problems) console.error(`  - ${p}`)
  console.error(
    '\n修复新增告警后再提交；确属误报请在代码中加精确抑制注解，' +
      '\n而不是放宽基线（放宽基线会一次性让所有历史欠账重新变成"可接受"）。',
  )
  process.exit(1)
}

console.log('\n✅ 静态分析未劣化 — 存量不阻塞，增量不放松')
