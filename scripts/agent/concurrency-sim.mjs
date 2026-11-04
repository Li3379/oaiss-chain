#!/usr/bin/env node
/**
 * 多 Agent 并发模拟验证 —— 用真实 git 行为证明护栏有效，而不是靠断言文档。
 *
 * ============================================================
 * 为什么需要它
 * ============================================================
 * 「我们有防冲突机制」这句话在 2026-11-04 被证伪了一次：
 * 机制（钩子、租约、契约文档）全都存在，但本地 main 依然积压了
 * 26 个未验证提交并与远端分叉出 8 处冲突——因为**没有任何一条路径被自动执行**。
 *
 * 所以验证不能是「读一遍脚本觉得对」，必须是**真的建 worktree、真的并行改、真的合并**，
 * 然后看结果是干净合入还是撞车。本脚本在临时目录里跑完整场景，不污染主工作区。
 *
 * ============================================================
 * 场景
 * ============================================================
 *   S1 路径隔离  两个 Agent 改不相交文件 → 合并必须无冲突
 *   S2 高频撞车  两个 Agent 改同一 i18n 文件不同位置 → 记录真实结果
 *   S3 版本撞车  两个 Agent 各自新建 V31 迁移 → pre-push 必须拦住
 *   S4 预检拦截  在落后远端的基线上开 Preflight → 必须 BLOCK
 *
 * 设计取舍：
 *   - 全部在 `os.tmpdir()` 下的临时仓库里做，用 `git clone --shared` 加速。
 *   - 每个场景独立、可单独运行，失败不互相污染。
 *   - S2 不做「必须无冲突」的断言：同文件并发改本来就是 Git 的能力边界，
 *     我们的策略是**禁止**（高危区独占租约），不是**解决**。所以这里验的是
 *     「冲突确实会发生」——用来证明独占租约不是多余的门槛。
 *
 * 用法：
 *   node scripts/agent/concurrency-sim.mjs            # 全部场景
 *   node scripts/agent/concurrency-sim.mjs --only S3  # 单个场景
 *
 * 退出码：0=全部符合预期，1=有场景不符预期
 */
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, rmSync, mkdirSync, copyFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const REPO = join(__dirname, '..', '..')

const argv = process.argv.slice(2)
const ONLY = argv.includes('--only') ? argv[argv.indexOf('--only') + 1] : null

// ------------------------------------------------------------------
// 工具
// ------------------------------------------------------------------
let workRoot = ''

function sh(cwd, cmd, args, opts = {}) {
  const { input, ...rest } = opts
  // execFileSync 不接受 input，需要喂 stdin 时走 spawnSync（pre-push 钩子从 stdin 读 ref 行）。
  // 注意：两者失败语义不同 —— execFileSync 抛异常，spawnSync 只把非零码放在 .status 上。
  // 不做这个区分的话，钩子拒绝推送会被误判为通过（第一版就是这么错的）。
  if (input !== undefined) {
    const r = spawnSync(cmd, args, {
      cwd,
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe'],
      input,
      ...rest,
    })
    return {
      ok: r.status === 0,
      out: ((r.stdout || '') + (r.stderr || '')).trim(),
      code: typeof r.status === 'number' ? r.status : 1,
    }
  }
  try {
    const out = execFileSync(cmd, args, {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      ...rest,
    })
    return { ok: true, out: (out || '').trim(), code: 0 }
  } catch (e) {
    return {
      ok: false,
      out: ((e.stdout || '') + (e.stderr || '')).trim(),
      code: typeof e.status === 'number' ? e.status : 1,
    }
  }
}

function g(cwd, ...args) {
  return sh(cwd, 'git', args)
}

/** 建一个隔离的「假远端 + 两个 Agent 工作区」沙盒 */
function makeSandbox(name) {
  const dir = join(workRoot, name)
  mkdirSync(dir, { recursive: true })

  // bare 远端
  const remote = join(dir, 'remote.git')
  g(dir, 'init', '--bare', '--initial-branch=main', remote)
  // seed 仓库：只放一个最小提交，避免搬运整个大仓
  const seed = join(dir, 'seed')
  g(dir, 'init', '--initial-branch=main', seed)
  g(seed, 'config', 'user.email', 'sim@local')
  g(seed, 'config', 'user.name', 'sim')
  mkdirSync(join(seed, 'oaiss-chain-frontend/src/i18n/locales'), { recursive: true })
  mkdirSync(join(seed, 'oaiss-chain-backend/src/main/resources/db/migration'), { recursive: true })
  writeFileSync(join(seed, 'README.md'), '# sim\n')
  writeFileSync(
    join(seed, 'oaiss-chain-frontend/src/i18n/locales/zh-CN.ts'),
    'export default {\n  a: 1,\n  b: 2,\n  c: 3,\n}\n',
  )
  writeFileSync(join(seed, 'oaiss-chain-backend/src/main/resources/db/migration/V30__base.sql'), '-- base\n')
  g(seed, 'add', '-A')
  g(seed, 'commit', '-m', 'chore(sim): 初始基线')
  g(seed, 'remote', 'add', 'origin', remote)
  g(seed, 'push', '-u', 'origin', 'main')
  // bare 仓库创建时 HEAD 可能不指向 main，clone 会检不出工作树。
  // 显式设置远端 HEAD，否则 agent 目录是空的，后续写入会因目录不存在而失败。
  g(remote, 'symbolic-ref', 'HEAD', 'refs/heads/main')

  // 两个 Agent 工作区
  const agents = {}
  for (const a of ['agentA', 'agentB']) {
    const p = join(dir, a)
    g(dir, 'clone', remote, p)
    g(p, 'config', 'user.email', `${a}@local`)
    g(p, 'config', 'user.name', a)
    agents[a] = p
  }
  return { dir, remote, seed, agents }
}

const results = []
function record(id, name, expected, actual, pass, note) {
  results.push({ id, name, expected, actual, pass, note })
  const mark = pass ? '✓' : '✗'
  console.log(`${mark} ${id} ${name}`)
  console.log(`    期望: ${expected}`)
  console.log(`    实际: ${actual}`)
  if (note) console.log(`    说明: ${note}`)
  console.log('')
}

// ------------------------------------------------------------------
// S1：路径隔离 → 合并无冲突
// ------------------------------------------------------------------
function scenario1() {
  const sb = makeSandbox('s1')
  const A = sb.agents.agentA
  const B = sb.agents.agentB

  // A 改后端；B 改前端。不相交。
  mkdirSync(join(A, 'oaiss-chain-backend/src/main/java'), { recursive: true })
  writeFileSync(join(A, 'oaiss-chain-backend/src/main/java/A.java'), 'class A {}\n')
  g(A, 'add', '-A')
  g(A, 'commit', '-m', 'feat(sim): agentA 改后端')

  writeFileSync(join(B, 'oaiss-chain-frontend/NOTES.md'), 'agentB\n')
  g(B, 'add', '-A')
  g(B, 'commit', '-m', 'feat(sim): agentB 改前端')

  // A 先合入 main，B 再 rebase + merge
  g(A, 'push', 'origin', 'main')
  g(B, 'fetch', 'origin')
  const rebase = g(B, 'rebase', 'origin/main')
  const merge = g(B, 'merge', '--no-edit', '--no-ff', 'origin/main')
  const conflicts = g(B, 'diff', '--name-only', '--diff-filter=U')
  const hasConflict = conflicts.ok && conflicts.out.length > 0

  record(
    'S1',
    '路径隔离：两个 Agent 改不相交文件',
    'rebase 与 merge 均成功，0 冲突文件',
    `rebase=${rebase.ok ? 'ok' : 'fail'}, merge=${merge.ok ? 'ok' : 'fail'}, 冲突文件=${hasConflict ? conflicts.out : '0'}`,
    rebase.ok && !hasConflict,
    '证明「一个任务 = 一个 worktree + 路径租约」这一策略在边界清晰时是充分有效的。',
  )
  return sb
}

// ------------------------------------------------------------------
// S2：同文件并发改 → 冲突确实发生（证明独占租约必要）
// ------------------------------------------------------------------
function scenario2() {
  const sb = makeSandbox('s2')
  const A = sb.agents.agentA
  const B = sb.agents.agentB
  const rel = 'oaiss-chain-frontend/src/i18n/locales/zh-CN.ts'

  // 双方都改**同一行**——这才是 i18n 巨型对象真正的撞车形态：
  // 两个人各在末尾追加词条时 Git 通常能自动合并（换行不同位置），
  // 只有当插入点落在同一行附近（≤1 行间隔）时才会产生 hunk 冲突。
  writeFileSync(join(A, rel), 'export default {\n  a: 1,\n  aNew: 10,\n  b: 2,\n  c: 3,\n}\n')
  g(A, 'add', '-A')
  g(A, 'commit', '-m', 'feat(sim): agentA 加词条')

  writeFileSync(join(B, rel), 'export default {\n  a: 1,\n  bNew: 20,\n  b: 2,\n  c: 3,\n}\n')
  g(B, 'add', '-A')
  g(B, 'commit', '-m', 'feat(sim): agentB 加词条')

  g(A, 'push', 'origin', 'main')
  g(B, 'fetch', 'origin')
  const merge = g(B, 'merge', '--no-edit', 'origin/main')
  const conflicts = g(B, 'diff', '--name-only', '--diff-filter=U')
  // 冲突时 merge 退出非零，冲突路径列在 --diff-filter=U 里
  const conflicted = !merge.ok && conflicts.out.includes('zh-CN.ts')

  record(
    'S2',
    '同文件并发改（i18n 巨型对象）',
    '合并产生冲突 —— 这证明「高危路径必须独占」不是多余门槛',
    conflicted ? '产生冲突（符合预期）' : `未产生冲突（merge.ok=${merge.ok}）`,
    conflicted,
    '策略是「禁止同改」而非「解决同改」：租约把隐性冲突提前到动手前暴露。',
  )
  return sb
}

// ------------------------------------------------------------------
// S3：迁移版本号撞车 → pre-push 拦截
// ------------------------------------------------------------------
function scenario3() {
  const sb = makeSandbox('s3')
  const A = sb.agents.agentA
  const B = sb.agents.agentB
  const mig = 'oaiss-chain-backend/src/main/resources/db/migration'

  // 两侧各自按「本地最大号 30」+1，都造出 V31
  writeFileSync(join(A, `${mig}/V31__agent_a.sql`), '-- a\n')
  g(A, 'add', '-A')
  g(A, 'commit', '-m', 'feat(sim): agentA 加 V31')

  writeFileSync(join(B, `${mig}/V31__agent_b.sql`), '-- b\n')
  g(B, 'add', '-A')
  g(B, 'commit', '-m', 'feat(sim): agentB 加 V31')

  // A 先推，B 落后远端
  g(A, 'push', 'origin', 'main')
  g(B, 'fetch', 'origin')

  // 用真实仓库的 pre-push 钩子跑 B 的推送
  const hook = join(REPO, '.githooks/pre-push')
  const headSha = g(B, 'rev-parse', 'HEAD').out
  const remoteSha = g(B, 'rev-parse', 'origin/main').out
  const stdin = `refs/heads/main ${headSha} refs/heads/main ${remoteSha}\n`
  const r = sh(B, 'bash', [hook, 'origin'], { input: stdin })

  // 钩子里包含「禁止直推 main」，所以必然会拦——需要区分是哪一个原因拦的。
  // 用 ALLOW_PUSH_MAIN=1 单独验证 Flyway 水位线这一道。
  const rMig = sh(B, 'bash', [hook, 'origin'], {
    input: stdin,
    env: { ...process.env, ALLOW_PUSH_MAIN: '1' },
  })
  const blockedByMigration = !rMig.ok && /Flyway|V31|水位线|out-of-order/.test(rMig.out)

  record(
    'S3',
    'Flyway 版本号撞车（两侧各自 +1 到 V31）',
    'pre-push 以 Flyway 水位线为由拒绝（即使放开直推 main）',
    blockedByMigration ? '被 Flyway 护栏拦截' : `未按预期拦截（exit=${rMig.code}）`,
    blockedByMigration,
    `附：默认护栏先以「禁止直推 main」拦下（exit=${r.code}），两层叠加。`,
  )
  return sb
}

// ------------------------------------------------------------------
// S5：小数版本号（V29_1）不得被误判为「命名不合规」
// ------------------------------------------------------------------
// 这是一次真实事故的回归锁：pre-push 的 version_of() 原本只认 ^V([0-9]+)__，
// V29_1 解析为空 → 被判成命名不合规 → 整条推送 exit 1。表现是 main 上
// 13 个提交全部推不出去，护栏从「保护」变成「封锁」。
//
// 本场景同时锁住三件事：
//   1 小数版本不再触发「命名不合规」
//   2 out-of-order 关闭时，低于水位线仍必须硬拦（保护没被削弱）
//   3 out-of-order 开启时降级为提示（与运行期策略一致）
function scenario5() {
  const sb = makeSandbox('s5')
  const A = sb.agents.agentA
  const mig = 'oaiss-chain-backend/src/main/resources/db/migration'
  const hook = join(REPO, '.githooks/pre-push')

  // 基线已有 V30（makeSandbox 种入），这里新增 V29_1——版本号低于水位线
  writeFileSync(join(A, `${mig}/V29_1__dedupe.sql`), '-- dedupe\n')
  g(A, 'add', '-A')
  g(A, 'commit', '-m', 'fix(db): agentA 加 V29_1 数据前置修复')

  const headSha = g(A, 'rev-parse', 'HEAD').out
  const remoteSha = g(A, 'rev-parse', 'origin/main').out
  const stdin = `refs/heads/main ${headSha} refs/heads/main ${remoteSha}\n`

  // 情形 A：out-of-order 未开启（沙盒里没有 application.yml）
  //         期望：仍被 Flyway 水位线拦下，且**不得**报「命名不合规」
  const rStrict = sh(A, 'bash', [hook, 'origin'], {
    input: stdin,
    env: { ...process.env, ALLOW_PUSH_MAIN: '1' },
  })
  const namingFalsePositive = /命名不合规/.test(rStrict.out)
  const blockedByWatermark = /Flyway 版本冲突/.test(rStrict.out)

  // 情形 B：out-of-order 开启 → 降级为提示，不阻塞
  mkdirSync(join(A, 'oaiss-chain-backend/src/main/resources'), { recursive: true })
  writeFileSync(
    join(A, 'oaiss-chain-backend/src/main/resources/application.yml'),
    'spring:\n  flyway:\n    out-of-order: true\n',
  )
  g(A, 'add', '-A')
  g(A, 'commit', '-m', 'chore(sim): 开启 flyway out-of-order')
  const headSha2 = g(A, 'rev-parse', 'HEAD').out
  const stdin2 = `refs/heads/main ${headSha2} refs/heads/main ${remoteSha}\n`
  const rOoo = sh(A, 'bash', [hook, 'origin'], {
    input: stdin2,
    env: { ...process.env, ALLOW_PUSH_MAIN: '1' },
  })

  const pass = !namingFalsePositive && blockedByWatermark && rOoo.ok
  record(
    'S5',
    '小数迁移版本（V29_1）不得误判为命名不合规',
    '关闭乱序时按水位线拦截且不报命名错误；开启乱序时降级为提示',
    namingFalsePositive
      ? '误报「命名不合规」（回归）'
      : `拦截=${blockedByWatermark ? '水位线' : '无'}，乱序放行=${rOoo.ok ? 'yes' : 'no'}`,
    pass,
    '真实事故回归锁：version_of 只认 ^V([0-9]+)__ 时，V29_1 解析为空导致推送全被封锁。',
  )
  return sb
}

// ------------------------------------------------------------------
// S4：落后远端时开工 → Preflight 必须 BLOCK
// ------------------------------------------------------------------
function scenario4() {
  const sb = makeSandbox('s4')
  const A = sb.agents.agentA
  const B = sb.agents.agentB

  g(A, 'push', 'origin', 'main') // 无改动，仅确保同步
  writeFileSync(join(A, 'DEV-GUIDE.md'), 'a\n')
  g(A, 'add', '-A')
  g(A, 'commit', '-m', 'docs(sim): agentA 推进远端')
  g(A, 'push', 'origin', 'main')

  // B 不 fetch，仍在旧基线上——正是本次事故的形态
  const script = join(REPO, 'scripts/agent/preflight.mjs')
  // preflight 以自身文件位置推导 ROOT，必须放进沙盒里才能检查沙盒
  const sandboxScriptDir = join(B, 'scripts/agent')
  mkdirSync(sandboxScriptDir, { recursive: true })
  copyFileSync(script, join(sandboxScriptDir, 'preflight.mjs'))

  // B 的 origin/main 引用还是旧的 → 需要让沙盒知道远端有新提交但不 fetch
  // 做法：B fetch 一次让 refs 更新，再 reset 回旧基线模拟「落后但知道」
  g(B, 'fetch', 'origin')
  const r = sh(B, 'node', [join(sandboxScriptDir, 'preflight.mjs'), '--json'])

  let parsed = null
  try {
    parsed = JSON.parse(r.out)
  } catch {
    /* 输出非 JSON 说明脚本没正常跑完 */
  }
  const behindBlocked = parsed && parsed.findings.some((f) => f.code === 'BEHIND_ORIGIN' && f.level === 'BLOCK')

  record(
    'S4',
    '在落后远端的基线上开工（本次事故形态）',
    'preflight 以 BEHIND_ORIGIN 阻断（exit=1）',
    r.code === 1 && behindBlocked ? '已阻断' : `未阻断（exit=${r.code}）`,
    Boolean(r.code === 1 && behindBlocked),
    '这是本次 8 处冲突的上游成因：落后 1 个提交就足以让两侧各自实现同一需求。',
  )
  return sb
}

// ------------------------------------------------------------------
// 主流程
// ------------------------------------------------------------------
const scenarios = { S1: scenario1, S2: scenario2, S3: scenario3, S4: scenario4, S5: scenario5 }
const picked = ONLY ? { [ONLY]: scenarios[ONLY] } : scenarios

if (ONLY && !scenarios[ONLY]) {
  console.error(`未知场景: ${ONLY}（可选 ${Object.keys(scenarios).join(' / ')}）`)
  process.exit(2)
}

workRoot = mkdtempSync(join(tmpdir(), 'oaiss-concurrency-sim-'))
console.log(`=== 多 Agent 并发模拟（沙盒: ${workRoot}）===\n`)

const sandboxes = []
try {
  for (const [id, fn] of Object.entries(picked)) {
    if (typeof fn !== 'function') continue
    console.log(`--- ${id} ---`)
    sandboxes.push(fn())
  }
} finally {
  // 清理：沙盒是临时验证产物，不留在磁盘上
  for (const sb of sandboxes) {
    try {
      rmSync(sb.dir, { recursive: true, force: true })
    } catch {
      /* 清理失败不影响结论 */
    }
  }
  try {
    rmSync(workRoot, { recursive: true, force: true })
  } catch {
    /* 同上 */
  }
}

const passed = results.filter((r) => r.pass).length
console.log(`=== 结果：${passed}/${results.length} 符合预期 ===`)
if (passed !== results.length) {
  console.log('\n不符预期的场景：')
  results.filter((r) => !r.pass).forEach((r) => console.log(`  ${r.id} ${r.name}`))
}
process.exit(passed === results.length ? 0 : 1)
