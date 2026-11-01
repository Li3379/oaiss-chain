#!/usr/bin/env node
/**
 * 版本一致性门禁。
 *
 * 背景：诊断时发现三处版本号互不相干——
 *   pom.xml                          1.0.0
 *   oaiss-chain-backend/pom.xml      1.0.0
 *   oaiss-chain-frontend/package.json  0.0.0   （Vite 脚手架默认值，从未改过）
 *   CHANGELOG.md                     1.7.1   （唯一持续维护的）
 *
 * 这意味着：任何「把 1.7.1 的镜像当 1.0.0 发布」「按 package.json 判断
 * 前端是哪个版本」的动作都会得到错误结论。版本号一旦不可信，
 * 回滚判断、问题复现、制品追溯就全部失去依据。
 *
 * 本门禁把 CHANGELOG.md 作为**版本号的唯一事实源**（它是最先被维护的），
 * 强制其余三处与它对齐。
 *
 * 为什么不让 pom 当事实源：CHANGELOG 是给人和发布流程看的，
 * 版本号语义（新增了什么、有没有破坏性变更）只有它记录得清楚。
 *
 * 用法：node scripts/gates/version-verify.mjs
 * 退出码：0=一致，1=漂移
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..', '..')

const problems = []
const notes = []

// ------------------------------------------------------------------
// 1. CHANGELOG 最新版本 = 事实源
// ------------------------------------------------------------------
const changelogPath = join(ROOT, 'CHANGELOG.md')
if (!existsSync(changelogPath)) {
  console.error('✗ 缺少 CHANGELOG.md')
  process.exit(1)
}
const changelog = readFileSync(changelogPath, 'utf8')

// 只认二级标题下的版本段，例如 "## [1.7.1] - 2026-10-31"
const versionHeadings = [...changelog.matchAll(/^##\s+\[(\d+\.\d+\.\d+)\]\s*-\s*(\d{4}-\d{2}-\d{2})/gm)]
if (versionHeadings.length === 0) {
  console.error('✗ CHANGELOG.md 中没有找到形如 "## [1.2.3] - YYYY-MM-DD" 的版本段')
  process.exit(1)
}
const [srcVersion, srcDate] = [versionHeadings[0][1], versionHeadings[0][2]]

// SemVer 合法性 + 顺序单调（新版本必须排在旧版本前面）
const semverRe = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/
if (!semverRe.test(srcVersion)) {
  problems.push(`CHANGELOG 最新版本号 "${srcVersion}" 不是合法 SemVer（MAJOR.MINOR.PATCH）`)
}
for (let i = 1; i < versionHeadings.length; i++) {
  const [cur, prev] = [versionHeadings[i][1], versionHeadings[i - 1][1]]
  const cmp = compareSemver(prev, cur)
  if (cmp !== null && cmp < 0) {
    problems.push(`CHANGELOG 版本顺序错乱：${prev} 排在 ${cur} 之前，但 ${prev} 更旧`)
  }
}

function compareSemver(a, b) {
  const pa = a.split('.').map(Number)
  const pb = b.split('.').map(Number)
  for (let i = 0; i < 3; i++) {
    if (pa[i] !== pb[i]) return pa[i] - pb[i] > 0 ? 1 : -1
  }
  return 0
}

console.log(`CHANGELOG 最新版本: ${srcVersion} (${srcDate})`)

// ------------------------------------------------------------------
// 2. Maven 两处 pom 必须一致且等于事实源
// ------------------------------------------------------------------
function readPomVersion(p) {
  if (!existsSync(p)) return null
  const xml = readFileSync(p, 'utf8')
  // 匹配 <project> 直属的 <version>：跳过 <parent> 块内的版本
  const withoutParent = xml.replace(/<parent>[\s\S]*?<\/parent>/g, '')
  const m = withoutParent.match(/<version>\s*([^<\s]+)\s*<\/version>/)
  return m ? m[1] : null
}

const rootPomVersion = readPomVersion(join(ROOT, 'pom.xml'))
const backendPomVersion = readPomVersion(join(ROOT, 'oaiss-chain-backend', 'pom.xml'))

for (const [label, v] of [
  ['根 pom.xml', rootPomVersion],
  ['oaiss-chain-backend/pom.xml', backendPomVersion],
]) {
  if (v === null) {
    problems.push(`${label} 未找到 <version>`)
  } else if (v !== srcVersion) {
    problems.push(`${label} 版本 ${v} ≠ CHANGELOG ${srcVersion}`)
  } else {
    console.log(`✓ ${label}: ${v}`)
  }
}

// ------------------------------------------------------------------
// 3. 前端 package.json 必须一致
// ------------------------------------------------------------------
const pkgPath = join(ROOT, 'oaiss-chain-frontend', 'package.json')
let pkgVersion = null
if (existsSync(pkgPath)) {
  try {
    pkgVersion = JSON.parse(readFileSync(pkgPath, 'utf8')).version
  } catch (e) {
    problems.push(`oaiss-chain-frontend/package.json 解析失败：${e.message}`)
  }
}
if (pkgVersion && pkgVersion !== srcVersion) {
  problems.push(
    `oaiss-chain-frontend/package.json 版本 ${pkgVersion} ≠ CHANGELOG ${srcVersion}` +
      (pkgVersion === '0.0.0' ? '（0.0.0 是脚手架默认值，说明从未同步过）' : ''),
  )
} else if (pkgVersion) {
  console.log(`✓ oaiss-chain-frontend/package.json: ${pkgVersion}`)
}

// ------------------------------------------------------------------
// 4. 已打 tag 必须存在于 CHANGELOG（不检查未打 tag，那是正常的）
// ------------------------------------------------------------------
try {
  const tags = execFileSync('git', ['tag', '--list', 'v*'], {
    cwd: ROOT,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  })
    .split('\n')
    .map((t) => t.trim())
    .filter(Boolean)

  const declared = new Set(versionHeadings.map((h) => `v${h[1]}`))
  const orphanTags = tags.filter((t) => !declared.has(t))
  if (orphanTags.length > 0) {
    problems.push(`以下 tag 在 CHANGELOG 中没有对应段落：${orphanTags.join(', ')}`)
  }
  for (const t of tags) {
    if (declared.has(t)) console.log(`✓ tag ${t} 有对应 CHANGELOG 段落`)
  }
} catch {
  notes.push('无法读取 git tag（可能不在仓库内），跳过 tag 校验')
}

// ------------------------------------------------------------------
if (problems.length > 0) {
  console.error(`\n❌ ${problems.length} 项版本漂移：`)
  for (const p of problems) console.error(`  - ${p}`)
  console.error(
    `\n修复：把上列文件的版本号统一为 ${srcVersion}。\n` +
      '发布新版本时，先在 CHANGELOG.md 顶部新增段落，再同步其余文件——\n' +
      'CHANGELOG 是事实源，其余三处跟随。',
  )
  process.exit(1)
}

for (const n of notes) console.log(`提示：${n}`)
console.log(`\n✅ 版本一致 — ${srcVersion}（pom / package.json / CHANGELOG 三方对齐）`)
