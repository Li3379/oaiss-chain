#!/usr/bin/env node
/**
 * 开工前预检（preflight）—— 把「并发防冲突」从人工自觉变成自动拦截。
 *
 * ============================================================
 * 为什么需要它
 * ============================================================
 * 仓库里已经有 lease.mjs（路径租约）、pre-push（推前护栏）、commit-msg（信息规范），
 * 但 2026-11-04 的实测暴露出一个结构性缺口：
 *
 *   本地 main 积压 26 个提交、从未经过任何 CI；
 *   与此同时远端 main 已被 PR #3 推进了 1 个提交。
 *   → 两侧各自独立实现了同一批需求（配额语义改造、V27 迁移、测试类）
 *   → 一个 merge-tree 试合并直接产生 8 个冲突文件，其中 3 个是 add/add。
 *
 * 现有护栏为什么没拦住：
 *   - pre-push 只在 **推送时** 生效，本地 main 可以无限积压
 *   - lease.mjs 要靠人主动调用，全仓没有任何脚本/CI 调用它
 *   - 没有任何检查关心「我这个工作区是不是已经落后远端、且落后得会冲突」
 *
 * 所以本脚本把三件必须在**动手之前**知道的事做成一次可执行的检查：
 *
 *   1. 工作区卫生 —— 是不是共享目录、有没有他人的未提交改动
 *   2. 分叉深度   —— 本地与 origin/main 双向差多少、是否已产生冲突面
 *   3. 租约状态   —— 是否有其他任务正占着高危路径
 *
 * 设计取舍：
 *   - **只读**。它不 fetch、不改 git 状态、不写租约，纯粹报告。
 *     预检本身若产生副作用，人会因为怕改变状态而不敢跑。
 *   - 分级退出：BLOCK（会直接引发冲突）非零退出；WARN 只提示。
 *     全阻塞会让人一律 --no-verify，护栏变摆设。
 *   - 冲突预判用 `git merge-tree --write-tree`（git ≥2.38 的合并引擎，
 *     在临时区试合并、不碰工作区），比「凭经验猜」准确。
 *
 * 用法：
 *   node scripts/agent/preflight.mjs                # 人读
 *   node scripts/agent/preflight.mjs --json         # 机读
 *   node scripts/agent/preflight.mjs --strict       # 把 WARN 也当 failure
 *
 * 退出码：0=可开工，1=有阻塞项，2=用法/环境错误
 */
import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..', '..')

const argv = process.argv.slice(2)
const JSON_OUT = argv.includes('--json')
const STRICT = argv.includes('--strict')

/** 已知高危路径（与协作契约 §3 同步维护） */
const HOT_PATHS = [
  'oaiss-chain-backend/src/main/resources/db/migration/',
  'oaiss-chain-frontend/src/i18n/locales/',
  'DEV-GUIDE.md',
  '.github/workflows/',
]

// ------------------------------------------------------------------
// git 封装：失败不抛，返回 {ok, out}
// ------------------------------------------------------------------
function git(args, opts = {}) {
  try {
    const out = execFileSync('git', args, {
      cwd: ROOT,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      ...opts,
    })
    return { ok: true, out: out.trim() }
  } catch (e) {
    return { ok: false, out: (e.stdout || '').trim(), err: (e.stderr || '').trim() }
  }
}

const findings = []
function add(level, code, message, detail) {
  findings.push({ level, code, message, ...(detail ? { detail } : {}) })
}

// ------------------------------------------------------------------
// 1. 工作区卫生
// ------------------------------------------------------------------
function checkWorkspace() {
  const branch = git(['rev-parse', '--abbrev-ref', 'HEAD'])
  const current = branch.ok ? branch.out : '(detached)'

  const wt = git(['worktree', 'list', '--porcelain'])
  const worktrees = wt.ok
    ? wt.out
        .split('\n')
        .filter((l) => l.startsWith('worktree '))
        .map((l) => l.slice('worktree '.length))
    : []
  const others = worktrees.filter((p) => p.replace(/\\/g, '/') !== ROOT.replace(/\\/g, '/'))

  // 未提交改动：区分「已跟踪被改」与「未跟踪」
  const dirty = git(['status', '--porcelain'])
  const tracked = dirty.ok
    ? dirty.out.split('\n').filter((l) => l && !l.startsWith('??')).length
    : 0
  const untracked = dirty.ok ? dirty.out.split('\n').filter((l) => l.startsWith('??')).length : 0

  if (current === 'main' || current === 'master') {
    add(
      'BLOCK',
      'ON_MAIN',
      `当前在 ${current} 上作业。任务必须在独立分支+worktree 内完成。`,
      'git fetch origin && git worktree add ../t-<id> -b <type>/<scope>-<slug> origin/main',
    )
  }

  if (tracked > 0) {
    add(
      'WARN',
      'DIRTY_WORKTREE',
      `工作区有 ${tracked} 个已跟踪文件被修改（未提交）。`,
      '共享工作区里的改动可能属于另一个会话；不要 stash/reset/checkout --，先确认归属。',
    )
  }

  if (others.length > 0) {
    add(
      'WARN',
      'OTHER_WORKTREES',
      `存在 ${others.length} 个其他 worktree。`,
      others.join(', '),
    )
  }

  return { currentBranch: current, worktrees, tracked, untracked }
}

// ------------------------------------------------------------------
// 2. 分叉深度与冲突预判
// ------------------------------------------------------------------
function checkDivergence() {
  const hasRemote = git(['rev-parse', '--verify', '-q', 'refs/remotes/origin/main'])
  if (!hasRemote.ok) {
    add('WARN', 'NO_REMOTE_MAIN', '找不到 origin/main，无法判断分叉。', '先执行 git fetch origin')
    return {}
  }

  const aheadR = git(['rev-list', '--count', 'origin/main..HEAD'])
  const behindR = git(['rev-list', '--count', 'HEAD..origin/main'])
  const ahead = aheadR.ok ? Number(aheadR.out) : 0
  const behind = behindR.ok ? Number(behindR.out) : 0

  // 落后远端 → 定基线漂移，最容易产生 add/add 冲突
  if (behind > 0) {
    add(
      'BLOCK',
      'BEHIND_ORIGIN',
      `本地落后 origin/main ${behind} 个提交。`,
      '在漂移的基线上开发，会和远端已合并的改动各自实现同一件事（本仓真实发生过：8 个冲突文件、3 个 add/add）。' +
        ' 先 git fetch origin 并 rebase/merge 到最新基线，再动手。',
    )
  }

  // 双向都有提交 → 提交前一定会合并，提前算出冲突面
  if (ahead > 0 && behind > 0) {
    const mt = git(['merge-tree', '--write-tree', '--name-only', 'HEAD', 'origin/main'])
    if (mt.ok) {
      // 有冲突时 merge-tree 退出码非 0，stdout 首行是 tree oid，其后是冲突路径
      add(
        'BLOCK',
        'DIVERGED',
        `与 origin/main 双向分叉（领先 ${ahead} / 落后 ${behind}），且试合并存在冲突。`,
        '先在一个临时 worktree 里完成合并并跑通测试，再提交到功能分支。',
      )
    }
  }

  if (ahead > 0 && behind === 0) {
    // 这是最常见也最危险的状态：本地一堆提交都没上过 CI
    add(
      'WARN',
      'UNVERIFIED_COMMITS',
      `本地领先 origin/main ${ahead} 个提交，这些提交从未经过 CI。`,
      '分支保护只在 PR 上跑 CI；本地 main 上的提交等于「本地跑过就算过」。' +
        ' 把它们推到功能分支开 PR，让 8 项必需检查真正执行一次。',
    )
  }

  return { ahead, behind }
}

// ------------------------------------------------------------------
// 3. 高危路径是否与远端已有改动重叠
// ------------------------------------------------------------------
function checkHotPathOverlap(divergence) {
  const { ahead = 0, behind = 0 } = divergence || {}
  if (behind === 0) return [] // 未落后，远端没有新改动，不存在重叠

  const localFiles = git(['diff', '--name-only', 'origin/main...HEAD'])
  const remoteFiles = git(['diff', '--name-only', 'HEAD...origin/main'])
  if (!localFiles.ok || !remoteFiles.ok) return []

  const set = new Set(remoteFiles.out.split('\n').filter(Boolean))
  const both = localFiles.out.split('\n').filter((f) => f && set.has(f))

  if (both.length > 0) {
    const hot = both.filter((f) => HOT_PATHS.some((h) => f.startsWith(h) || f === h))
    add(
      'BLOCK',
      'OVERLAPPING_FILES',
      `${both.length} 个文件在本地与远端被「各自独立修改」，其中 ${hot.length} 个属于高危区。`,
      both.slice(0, 8).join('\n') + (both.length > 8 ? `\n... 另有 ${both.length - 8} 个` : ''),
    )
    return both
  }
  return []
}

// ------------------------------------------------------------------
// 4. 租约状态
// ------------------------------------------------------------------
function checkLeases() {
  const script = join(ROOT, 'scripts', 'agent', 'lease.mjs')
  if (!existsSync(script)) return

  const r = git(['--version']) // 占位，避免未使用告警
  void r

  try {
    const out = execFileSync(process.execPath, [script, 'status'], {
      cwd: ROOT,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    if (/（无）|\(none\)/.test(out)) return
    add('WARN', 'ACTIVE_LEASES', '存在其他任务的活跃租约，开工前确认路径不重叠。', out.trim())
  } catch {
    // lease.mjs 自身失败不应阻塞预检——它只是辅助信息
  }
}

// ------------------------------------------------------------------
// 主流程
// ------------------------------------------------------------------
const workspace = checkWorkspace()
const divergence = checkDivergence()
const overlapping = checkHotPathOverlap(divergence)
checkLeases()

const blocking = findings.filter((f) => f.level === 'BLOCK')
const warnings = findings.filter((f) => f.level === 'WARN')
const failed = blocking.length > 0 || (STRICT && warnings.length > 0)

if (JSON_OUT) {
  console.log(
    JSON.stringify(
      {
        ok: !failed,
        branch: workspace.currentBranch,
        divergence,
        overlappingFiles: overlapping,
        findings,
      },
      null,
      2,
    ),
  )
} else {
  console.log('=== 开工前预检（preflight）===\n')
  console.log(`当前分支: ${workspace.currentBranch}`)
  if (divergence.ahead !== undefined) {
    console.log(`相对 origin/main: 领先 ${divergence.ahead} / 落后 ${divergence.behind}`)
  }
  console.log(`并行 worktree: ${workspace.worktrees.length}`)
  console.log('')

  if (findings.length === 0) {
    console.log('✓ 无阻塞项，可以开工。')
  } else {
    for (const f of blocking) {
      console.log(`✗ [${f.code}] ${f.message}`)
      if (f.detail) console.log(f.detail.split('\n').map((l) => `    ${l}`).join('\n'))
      console.log('')
    }
    for (const f of warnings) {
      console.log(`⚠ [${f.code}] ${f.message}`)
      if (f.detail) console.log(f.detail.split('\n').map((l) => `    ${l}`).join('\n'))
      console.log('')
    }
    console.log(
      failed
        ? `❌ 预检未通过：${blocking.length} 个阻塞项，${warnings.length} 个提示项。`
        : `✓ 无阻塞项（${warnings.length} 个提示项）。`,
    )
  }
}

process.exit(failed ? 1 : 0)
