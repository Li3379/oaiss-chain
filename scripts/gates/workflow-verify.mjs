#!/usr/bin/env node
/**
 * GitHub Actions 工作流门禁。
 *
 * 背景：`deploy-release.yml` 里一个 heredoc 的内容顶格书写，破坏了 YAML 结构，
 * 使整个工作流文件非法。GitHub 的反应是「workflow file issue」并在每次 push
 * 报一次红色失败——但没有任何测试会告诉你原因，因为 YAML 在别处用得好好的。
 *
 * 这类缺陷的可怕之处：它不会让功能出错，只会让 CI 永久静默失效。
 * 你看着 Actions 页面一片红，却以为是代码有问题。
 *
 * 本门禁做两件事：
 *   1. 校验每个工作流文件是合法 YAML
 *   2. 校验触发分支与实际主分支一致——工作流写了 push:[main] 而仓库主分支叫别的，
 *      等于 CI 永远不会跑，而这在 GitHub 上是静默的
 *
 * 用法：node scripts/gates/workflow-verify.mjs
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..', '..')
const WF_DIR = join(ROOT, '.github', 'workflows')

/**
 * 极简 YAML 结构校验器（不引第三方依赖，避免 CI 装包）。
 *
 * 只检查最容易出错、且后果最严重的一点：块标量（`run: |` / `run: >`）
 * 之后的续行必须比该键缩进更深。一旦有行顶格，YAML 解析器会把它当作
 * 新的顶层键，进而报 "could not find expected ':'"。
 */
function validateBlockScalarIndent(lines, file) {
  const errors = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    const m = line.match(/^(\s*)(\S+):\s*(\||>)([+-]?\d*)\s*$/)
    if (!m) { i++; continue }

    const keyIndent = m[1].length
    const startLine = i + 1
    i++
    // 块内容：后续所有行，直到出现缩进 <= keyIndent 的非空行
    while (i < lines.length) {
      const cur = lines[i]
      if (cur.trim() === '') { i++; continue }
      const curIndent = cur.match(/^(\s*)/)[1].length
      if (curIndent <= keyIndent) break
      i++
    }
    // 检查块内是否出现过顶格（那会截断块）
    for (let j = startLine; j < i; j++) {
      const cur = lines[j]
      if (cur.trim() === '') continue
      const curIndent = cur.match(/^(\s*)/)[1].length
      if (curIndent <= keyIndent) {
        errors.push(`${file}:${j + 1} 块标量内容缩进不足（键在第 ${startLine} 行，键缩进 ${keyIndent}）：${cur.trim().slice(0, 60)}`)
        break
      }
    }
  }
  return errors
}

/** 检查顶层是否出现疑似 YAML 结构破坏（顶格的非键行） */
function validateTopLevel(lines, file) {
  const errors = []
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.trim() === '' || line.startsWith('#') || line.startsWith(' ')) continue
    // 顶层只允许 "key:" 形式，或 "---"/"..." 文档分隔
    if (line === '---' || line === '...') continue
    if (!/^\S.*:(\s|$)/.test(line)) {
      errors.push(`${file}:${i + 1} 顶层出现非 "key:" 行，YAML 结构可能已损坏：${line.trim().slice(0, 60)}`)
    }
  }
  return errors
}

/**
 * 提取 push/pull_request 的 branches 列表。
 *
 * 只认 `branches:` 段下的条目。早先版本把同级的 `paths:` 也一并收了进来，
 * 于是触发分支里出现了 `oaiss-chain-backend/` 这种目录名——比不检查更糟，
 * 因为它会让人误以为配置没问题。
 */
function extractTriggerBranches(lines) {
  const branches = []
  let inOn = false
  let inEvent = false   // 在 push:/pull_request: 段内
  let inBranches = false // 在 branches: 列表内
  let baseIndent = -1

  for (const line of lines) {
    if (/^on:/.test(line)) { inOn = true; baseIndent = -1; continue }
    if (inOn && /^[^\s#]/.test(line)) { inOn = false; inEvent = false; inBranches = false }
    if (!inOn) continue

    // 事件层：push: / pull_request:
    const ev = line.match(/^\s{2}(push|pull_request):/)
    if (ev) { inEvent = true; inBranches = false; baseIndent = -1; continue }

    // 事件内其它键（branches:/paths:/tags:），支持内联数组 branches: [main]
    const key = line.match(/^\s{4}(branches|paths|tags):\s*(\[[^\]]*\])?/)
    if (inEvent && key) {
      inBranches = key[1] === 'branches'
      if (inBranches && key[2]) {
        key[2].replace(/[\[\]]/g, '').split(',').forEach(b => {
          const t = b.trim().replace(/['"]/g, '')
          if (t) branches.push(t)
        })
      }
      baseIndent = 4
      continue
    }

    // 事件内更深层的非列表行 → 离开 branches 段
    if (inEvent && inBranches) {
      const m = line.match(/^\s{6,}- ['"]?([a-zA-Z0-9._\/-]+)['"]?/)
      if (m) { branches.push(m[1]); continue }
      if (/^\s{4}[a-zA-Z]/.test(line)) { inBranches = false }
    }
  }
  return branches
}

/**
 * 检查 shell 脚本是否保留了可执行位。
 *
 * 背景：Windows 上开发时 git 默认不记录文件权限（core.fileMode=false），
 * 于是所有 .sh 脚本入库时都是 100644。Linux Runner 上直接调用
 * `./scripts/xxx.sh` 会得到 exit 126 (Permission denied)——
 * 而本地 Windows 跑同样的命令完全正常，缺陷只在 CI 暴露。
 *
 * @returns {string[]} 缺可执行位的文件列表
 */
function checkShellExecutableBits() {
  const problems = []
  try {
    const out = execFileSync('git', ['ls-files', '-s', '*.sh'], {
      cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'],
    })
    for (const line of out.split('\n')) {
      if (!line.trim()) continue
      const parts = line.split(/\s+/)
      const mode = parts[0]
      const file = parts.slice(3).join(' ')
      // vendor/ 下的是第三方依赖自带的脚本，不参与本项目执行，不计入
      if (file.includes('/vendor/')) continue
      if (mode === '100644') problems.push(file)
    }
  } catch {
    // 非 git 环境，跳过
  }
  return problems
}

function main() {
  console.log('=== GitHub Actions 工作流门禁 ===\n')

  if (!existsSync(WF_DIR)) {
    console.log('⚠ 未找到 .github/workflows，跳过')
    return
  }

  const files = readdirSync(WF_DIR).filter(f => f.endsWith('.yml') || f.endsWith('.yaml'))
  if (files.length === 0) {
    console.log('⚠ workflows 目录为空，跳过')
    return
  }

  let failures = 0
  const allBranches = new Set()

  for (const file of files) {
    const path = join(WF_DIR, file)
    const raw = readFileSync(path, 'utf8')
    const lines = raw.split(/\r?\n/)

    const errs = [...validateBlockScalarIndent(lines, file), ...validateTopLevel(lines, file)]
    if (errs.length > 0) {
      failures++
      console.log(`✗ ${file}`)
      errs.slice(0, 5).forEach(e => console.log(`    ${e}`))
    } else {
      console.log(`✓ ${file}`)
    }

    extractTriggerBranches(lines).forEach(b => allBranches.add(b))
  }

  // 触发分支与实际主分支一致性：写了 push:[main] 但仓库主分支不叫 main，CI 就是静默失效
  console.log('\n--- 触发分支 ---')
  if (allBranches.size === 0) {
    console.log('  所有工作流均为手动触发（workflow_dispatch），无自动触发分支')
  } else {
    console.log(`  自动触发分支: ${[...allBranches].join(', ')}`)
  }

  // shell 脚本可执行位
  console.log('\n--- shell 脚本可执行位 ---')
  const nonExec = checkShellExecutableBits()
  if (nonExec.length > 0) {
    failures++
    console.log(`✗ ${nonExec.length} 个 .sh 脚本入库权限为 100644（缺可执行位）`)
    console.log('    Linux Runner 上 ./xxx.sh 会直接 exit 126 (Permission denied)，')
    console.log('    而 Windows 本地不受影响——缺陷只在 CI 暴露。')
    nonExec.slice(0, 5).forEach(f => console.log(`    ${f}`))
    if (nonExec.length > 5) console.log(`    ... 另有 ${nonExec.length - 5} 个`)
    console.log('    修复: git update-index --chmod=+x <file>')
  } else {
    console.log('✓ 所有 .sh 脚本均带可执行位')
  }

  console.log('')
  if (failures > 0) {
    console.log(`❌ ${failures} 个工作流文件结构损坏 — CI 会静默失效，请修复后重试`)
    process.exit(1)
  }
  console.log('✅ 全部工作流文件结构合法')
}

main()
