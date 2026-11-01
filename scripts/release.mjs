#!/usr/bin/env node
/**
 * 发布脚本：把「版本号 → tag → GitHub Release」串成一条可复核的流水。
 *
 * 为什么需要脚本而不是几条命令：
 *   版本发布最容易漏的不是打 tag，而是**前置条件**——
 *   在红色 CI 上打 tag、在没写 CHANGELOG 的版本上打 tag、
 *   在 main 落后于远端时打 tag。这些错误都能在 5 秒内检查出来，
 *   但人工执行时通常一条都不查。
 *
 * 本脚本只做本地检查与打 tag；推送与创建 Release 交给 --push 显式触发，
 * 避免「跑一下看看」顺手把 tag 推上去。
 *
 * 用法：
 *   node scripts/release.mjs 1.7.2              # 干跑：只检查，不动作
 *   node scripts/release.mjs 1.7.2 --tag        # 通过检查后打本地 tag
 *   node scripts/release.mjs 1.7.2 --tag --push # 打 tag 并推送到 origin
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..')

const argv = process.argv.slice(2)
const target = argv.find((a) => /^\d+\.\d+\.\d+$/.test(a))
const DO_TAG = argv.includes('--tag')
const DO_PUSH = argv.includes('--push')

if (!target) {
  console.error('用法: node scripts/release.mjs <MAJOR.MINOR.PATCH> [--tag] [--push]')
  process.exit(2)
}
if (DO_PUSH && !DO_TAG) {
  console.error('✗ --push 必须与 --tag 同时使用')
  process.exit(2)
}

const problems = []
const warnings = []

function git(args, opts = {}) {
  return execFileSync('git', args, { cwd: ROOT, encoding: 'utf8', ...opts }).trim()
}

function tryGit(args) {
  try {
    return git(args)
  } catch {
    return null
  }
}

console.log(`=== 发布前检查：v${target} ===\n`)

// ------------------------------------------------------------------
// 1. 版本号必须严格大于当前版本（SemVer 单调）
// ------------------------------------------------------------------
const changelog = readFileSync(join(ROOT, 'CHANGELOG.md'), 'utf8')
const headings = [...changelog.matchAll(/^##\s+\[(\d+\.\d+\.\d+)\]/gm)].map((m) => m[1])
const current = headings[0]

function cmp(a, b) {
  const pa = a.split('.').map(Number)
  const pb = b.split('.').map(Number)
  for (let i = 0; i < 3; i++) if (pa[i] !== pb[i]) return pa[i] - pb[i]
  return 0
}

if (current && cmp(target, current) <= 0) {
  problems.push(`目标版本 ${target} 不大于当前版本 ${current}。版本号只能单向前进。`)
} else {
  console.log(`✓ 版本递增: ${current} → ${target}`)
}

// ------------------------------------------------------------------
// 2. CHANGELOG 必须已有该版本的段落（先写文档，再发布）
// ------------------------------------------------------------------
if (!headings.includes(target)) {
  problems.push(
    `CHANGELOG.md 中没有 [${target}] 段落。\n` +
      `    发布前先写清楚「这个版本改了什么」——这是回滚判断和对外沟通的唯一依据。`,
  )
} else {
  console.log(`✓ CHANGELOG 已有 [${target}] 段落`)
}

// 段落不能是空壳（只有标题没有内容）。
// 不能用 `(?=^##\s+\[|\s*$)` 做终止：带 m 标志时 `\s*$` 会匹配**任意行尾**，
// 段落被立刻截断成 0 字符，于是每个版本都被误判成占位（实测踩过）。
// 改成按行切分后取到下一个二级标题为止，语义直白且不依赖正则回溯。
const sectionStart = changelog.search(new RegExp(`^##\\s+\\[${target.replace(/\./g, '\\.')}\\]`, 'm'))
if (sectionStart >= 0) {
  const rest = changelog.slice(sectionStart)
  const lines = rest.split('\n')
  const bodyLines = []
  for (let i = 1; i < lines.length; i++) {
    if (/^##\s+\[/.test(lines[i])) break
    bodyLines.push(lines[i])
  }
  const body = bodyLines.join('\n').trim()
  if (body.length < 30) {
    problems.push(`CHANGELOG 的 [${target}] 段落内容过少（${body.length} 字符），疑似占位。`)
  }
}

// ------------------------------------------------------------------
// 3. 三处版本号必须已对齐
// ------------------------------------------------------------------
try {
  execFileSync('node', [join(ROOT, 'scripts', 'gates', 'version-verify.mjs')], {
    cwd: ROOT,
    stdio: 'pipe',
    encoding: 'utf8',
  })
  console.log('✓ 版本一致性门禁通过')
} catch (e) {
  problems.push(
    '版本一致性门禁未通过（pom / package.json 与 CHANGELOG 不一致）。\n' +
      '    先跑 node scripts/gates/version-verify.mjs 看具体差异。',
  )
}

// ------------------------------------------------------------------
// 4. 必须在 main 上，且工作区干净
// ------------------------------------------------------------------
const branch = tryGit(['rev-parse', '--abbrev-ref', 'HEAD'])
if (branch !== 'main') {
  problems.push(`当前分支是 "${branch}"，发布必须在 main 上进行。`)
} else {
  console.log('✓ 当前在 main 分支')
}

const dirty = tryGit(['status', '--porcelain'])
if (dirty) {
  const lines = dirty.split('\n').length
  problems.push(`工作区有 ${lines} 个未提交改动。发布必须基于干净的树。`)
} else {
  console.log('✓ 工作区干净')
}

// ------------------------------------------------------------------
// 5. 不得落后于远端
// ------------------------------------------------------------------
const remoteUrl = tryGit(['remote', 'get-url', 'origin'])
if (remoteUrl) {
  tryGit(['fetch', 'origin', '--quiet'])
  const behind = tryGit(['rev-list', '--count', 'HEAD..origin/main'])
  if (behind && Number(behind) > 0) {
    problems.push(`本地 main 落后 origin/main ${behind} 个提交。先 git pull。`)
  } else {
    console.log('✓ 与 origin/main 同步')
  }

  // 6. 远端 CI 状态（有 gh 就用，没有只警告）
  try {
    const runs = execFileSync(
      'gh',
      ['run', 'list', '--branch', 'main', '--limit', '5', '--json', 'conclusion,headSha,workflowName'],
      { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
    )
    const parsed = JSON.parse(runs)
    const head = tryGit(['rev-parse', 'HEAD'])
    const forHead = parsed.filter((r) => r.headSha === head)
    const failed = forHead.filter((r) => r.conclusion === 'failure')
    if (forHead.length === 0) {
      warnings.push('当前提交尚无 CI 运行记录（刚推送时正常，建议等 CI 跑完再发布）')
    } else if (failed.length > 0) {
      problems.push(
        `当前提交的 CI 有 ${failed.length} 个失败：${failed.map((r) => r.workflowName).join(', ')}`,
      )
    } else {
      console.log(`✓ 当前提交 CI 全绿（${forHead.length} 个工作流）`)
    }
  } catch {
    warnings.push('无法查询 CI 状态（gh 未安装或未登录），请人工确认 main 全绿')
  }
} else {
  warnings.push('未配置 origin 远端，跳过同步与 CI 检查')
}

// ------------------------------------------------------------------
// 7. tag 不得已存在
// ------------------------------------------------------------------
if (tryGit(['rev-parse', `v${target}`])) {
  problems.push(`tag v${target} 已存在。`)
} else {
  console.log(`✓ tag v${target} 未占用`)
}

// ------------------------------------------------------------------
// 汇总
// ------------------------------------------------------------------
console.log('')
for (const w of warnings) console.log(`⚠ ${w}`)

if (problems.length > 0) {
  console.error(`\n❌ ${problems.length} 项前置条件未满足：\n`)
  problems.forEach((p, i) => console.error(`  ${i + 1}. ${p}`))
  console.error('\n发布已中止，未做任何改动。')
  process.exit(1)
}

console.log('\n✅ 全部前置条件满足')

if (!DO_TAG) {
  console.log('\n干跑结束。确认无误后执行：')
  console.log(`  node scripts/release.mjs ${target} --tag${DO_PUSH ? '' : ' --push'}`)
  process.exit(0)
}

// ------------------------------------------------------------------
// 打 tag（附注标签，带发布说明摘要）
// ------------------------------------------------------------------
const subject = changelog
  .slice(changelog.indexOf(`## [${target}]`))
  .split('\n')
  .slice(1)
  .map((l) => l.trim())
  .find((l) => l && !l.startsWith('#'))

git(['tag', '-a', `v${target}`, '-m', `Release v${target}\n\n${subject ?? ''}`])
console.log(`\n✓ 已创建附注标签 v${target}`)

if (DO_PUSH) {
  git(['push', 'origin', `v${target}`])
  console.log(`✓ 已推送到 origin`)
  console.log('\n下一步（创建 GitHub Release）：')
  console.log(`  gh release create v${target} --title "v${target}" --notes-from-tag`)
} else {
  console.log('\n推送：node scripts/release.mjs ' + target + ' --tag --push')
  console.log('或：  git push origin v' + target)
}
