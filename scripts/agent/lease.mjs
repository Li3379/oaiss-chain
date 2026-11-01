#!/usr/bin/env node
/**
 * 工作区租约（workspace lease）—— 多 Agent / 多会话并发作业的冲突前置拦截。
 *
 * ============================================================
 * 为什么需要这个东西
 * ============================================================
 * 这个仓库里已经真实发生过一次并发事故：
 *   会话 A 19:50 创建 V22__add_credit_delisted.sql（未提交）
 *   会话 B 19:47 提交并推送 V23__update_unit_price_comment.sql
 *   两者共享同一个工作区目录，但彼此「看不见」对方的意图
 *   → 版本号撞车 → 已部署环境 Flyway out-of-order 拒绝启动
 *
 * Git 本身对此无能为力：文件还没提交，git status 只显示「有改动」，
 * 说不出「谁在改、改到哪一步、还要多久」。
 *
 * 租约不是锁：它不阻止任何人写文件（技术上做不到），
 * 它做的是**在动手之前把隐性冲突变成显性提示**。
 *
 * ============================================================
 * 设计取舍
 * ============================================================
 * 1. 租约文件放在 .agent-leases/ 且**不入库**（.gitignore）。
 *    入库会导致每次抢租约都产生一个提交、合并时冲突——
 *    用版本控制管理锁是反模式。
 * 2. 租约按「路径前缀」声明，而不是逐个文件。
 *    Agent 改一个模块通常涉及 controller/service/test 多个文件，
 *    逐文件声明太细，人会懒得写。
 * 3. 过期即失效（默认 2 小时）。无人续租的租约不该永久挡人。
 *    续租用 --renew。
 * 4. 冲突分两级：**硬冲突**（同一路径前缀重叠）默认阻塞；
 *    **软冲突**（同目录不同文件）只警告。全阻塞会让人绕过工具。
 *
 * ============================================================
 * 用法
 * ============================================================
 *   node scripts/agent/lease.mjs status
 *   node scripts/agent/lease.mjs acquire --task "R-03 下市处罚" --paths "oaiss-chain-backend/src/main/java/com/oaiss/chain/service/CreditScoreService.java,oaiss-chain-backend/src/main/resources/db/migration/"
 *   node scripts/agent/lease.mjs release --task "R-03 下市处罚"
 *   node scripts/agent/lease.mjs check --paths "..."     # 只检查不占用
 *   node scripts/agent/lease.mjs renew --task "..."
 *   node scripts/agent/lease.mjs prune                   # 清理过期租约
 *
 * 退出码：0=无冲突，1=有硬冲突，2=用法错误
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync, unlinkSync } from 'node:fs'
import { join, dirname, normalize, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { hostname } from 'node:os'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(__dirname, '..', '..')
const LEASE_DIR = join(ROOT, '.agent-leases')

const DEFAULT_TTL_MINUTES = 120

// ------------------------------------------------------------------
// 参数解析：不引第三方依赖，保持 CI 里零安装
// ------------------------------------------------------------------
function parseArgs(argv) {
  const out = { _: [] }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a.startsWith('--')) {
      const key = a.slice(2)
      const next = argv[i + 1]
      if (next === undefined || next.startsWith('--')) {
        out[key] = true
      } else {
        out[key] = next
        i++
      }
    } else {
      out._.push(a)
    }
  }
  return out
}

const args = parseArgs(process.argv.slice(2))
const command = args._[0]

// ------------------------------------------------------------------
// 路径归一化：跨平台一致（Windows 反斜杠 / 大小写）
// ------------------------------------------------------------------
function normPath(p) {
  let n = p.trim().replace(/\\/g, '/')
  // 去掉开头的 ./ 与尾部的 /
  n = n.replace(/^\.\//, '').replace(/\/+$/, '')
  // 限制在仓库内，防止 ../ 逃逸
  const abs = resolve(ROOT, n)
  const rel = abs.startsWith(ROOT) ? abs.slice(ROOT.length + 1) : n
  return rel.split(sep).join('/')
}

function splitPaths(raw) {
  if (!raw || raw === true) return []
  return String(raw)
    .split(/[,;\n]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map(normPath)
}

/** 两个路径是否有重叠（前缀关系） */
function overlaps(a, b) {
  if (a === b) return true
  const aP = a.endsWith('/') ? a : `${a}/`
  const bP = b.endsWith('/') ? b : `${b}/`
  return a.startsWith(bP) || b.startsWith(aP)
}

/** 同一目录（软冲突判定） */
function sameDir(a, b) {
  const da = a.includes('/') ? a.slice(0, a.lastIndexOf('/')) : ''
  const db = b.includes('/') ? b.slice(0, b.lastIndexOf('/')) : ''
  return da === db && da !== ''
}

// ------------------------------------------------------------------
// 租约存取
// ------------------------------------------------------------------
function ensureDir() {
  if (!existsSync(LEASE_DIR)) mkdirSync(LEASE_DIR, { recursive: true })
}

function leaseFile(task) {
  // 任务名 → 文件名：保留可读性，只替换文件系统非法字符
  const safe = task.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').slice(0, 120)
  return join(LEASE_DIR, `${safe}.json`)
}

function readLeases() {
  if (!existsSync(LEASE_DIR)) return []
  const leases = []
  for (const f of readdirSync(LEASE_DIR)) {
    if (!f.endsWith('.json')) continue
    const full = join(LEASE_DIR, f)
    try {
      const data = JSON.parse(readFileSync(full, 'utf8'))
      leases.push({ ...data, _file: full })
    } catch {
      // 损坏的租约不能静默忽略：它会让冲突检测出现盲区。
      // 但也不该让整个命令挂掉——标出来，让人处理。
      leases.push({ task: f, paths: [], broken: true, _file: full })
    }
  }
  return leases
}

function isExpired(lease) {
  if (!lease.expiresAt) return true
  return new Date(lease.expiresAt).getTime() <= Date.now()
}

function activeLeases() {
  return readLeases().filter((l) => !l.broken && !isExpired(l))
}

function whoami() {
  return {
    agent: process.env.AGENT_ID || process.env.CLAUDE_SESSION_ID || process.env.ZCODE_SESSION_ID || 'unknown-agent',
    host: hostname(),
    pid: process.pid,
  }
}

// ------------------------------------------------------------------
// 冲突检测
// ------------------------------------------------------------------
function detectConflicts(myPaths, myTask) {
  const hard = []
  const soft = []
  for (const lease of activeLeases()) {
    if (lease.task === myTask) continue // 续租自己不算冲突
    for (const mine of myPaths) {
      for (const theirs of lease.paths) {
        if (overlaps(mine, theirs)) {
          hard.push({ mine, theirs, lease })
        } else if (sameDir(mine, theirs)) {
          soft.push({ mine, theirs, lease })
        }
      }
    }
  }
  return { hard, soft }
}

function printLeaseLine(l) {
  const left = isExpired(l) ? '（已过期）' : ''
  const exp = l.expiresAt ? new Date(l.expiresAt).toLocaleString('zh-CN') : '-'
  console.log(`  • ${l.task}${left}`)
  console.log(`    agent=${l.agent}  host=${l.host}  到期=${exp}`)
  if (l.paths?.length) console.log(`    路径: ${l.paths.join(', ')}`)
  if (l.intent) console.log(`    意图: ${l.intent}`)
}

function formatConflict(c) {
  return `    - 我的 ${c.mine}\n      ↔ 他人 ${c.theirs}（任务「${c.lease.task}」，agent=${c.lease.agent}）`
}

// ------------------------------------------------------------------
// 命令实现
// ------------------------------------------------------------------
function cmdStatus() {
  const all = readLeases()
  const active = all.filter((l) => !l.broken && !isExpired(l))
  const expired = all.filter((l) => !l.broken && isExpired(l))
  const broken = all.filter((l) => l.broken)

  console.log('=== 活跃租约 ===')
  if (active.length === 0) console.log('  （无）')
  active.forEach(printLeaseLine)

  if (expired.length > 0) {
    console.log(`\n=== 过期租约（${expired.length}，可 prune 清理）===`)
    expired.forEach(printLeaseLine)
  }
  if (broken.length > 0) {
    console.log(`\n=== 损坏的租约文件（${broken.length}，需手工检查）===`)
    broken.forEach((b) => console.log(`  • ${b._file}`))
  }
  return 0
}

function cmdAcquire() {
  const task = args.task
  if (!task || task === true) {
    console.error('✗ 缺少 --task（任务标识，建议与分支名/工单号一致）')
    return 2
  }
  const paths = splitPaths(args.paths)
  if (paths.length === 0) {
    console.error('✗ 缺少 --paths（要改动的路径前缀，逗号分隔）')
    return 2
  }

  const { hard, soft } = detectConflicts(paths, task)

  if (hard.length > 0) {
    console.error('✗ 租约冲突：以下路径已被其他任务占用\n')
    hard.forEach((c) => console.error(formatConflict(c)))
    console.error(
      '\n处置方式（择一）：\n' +
        '  1. 等对方完成并 release（node scripts/agent/lease.mjs status 查看）\n' +
        '  2. 把工作拆到不重叠的路径（这是首选——冲突说明任务边界没切好）\n' +
        '  3. 确认对方已停止作业（status 显示过期）→ node scripts/agent/lease.mjs prune\n' +
        '\n不要用「先改再说，合并时再解决」来绕过：\n' +
        '  这个仓库已经因此产生过 Flyway 版本号撞车，修复成本远高于等待。\n',
    )
    return 1
  }

  if (soft.length > 0) {
    console.warn('⚠ 软冲突（同目录、不同文件）——不阻塞，但请留意：\n')
    soft.forEach((c) => console.warn(formatConflict(c)))
    console.warn('')
  }

  ensureDir()
  const ttl = Number(args.ttl ?? DEFAULT_TTL_MINUTES)
  const now = new Date()
  const lease = {
    task,
    paths,
    intent: args.intent && args.intent !== true ? String(args.intent) : undefined,
    branch: args.branch && args.branch !== true ? String(args.branch) : undefined,
    ...whoami(),
    createdAt: now.toISOString(),
    renewedAt: now.toISOString(),
    expiresAt: new Date(now.getTime() + ttl * 60_000).toISOString(),
    ttlMinutes: ttl,
  }
  writeFileSync(leaseFile(task), `${JSON.stringify(lease, null, 2)}\n`, 'utf8')

  console.log(`✓ 已取得租约：${task}`)
  console.log(`  路径: ${paths.join(', ')}`)
  console.log(`  到期: ${new Date(lease.expiresAt).toLocaleString('zh-CN')}（${ttl} 分钟后）`)
  console.log('\n完成后请释放：node scripts/agent/lease.mjs release --task "' + task + '"')
  return 0
}

function cmdCheck() {
  const paths = splitPaths(args.paths)
  if (paths.length === 0) {
    console.error('✗ 缺少 --paths')
    return 2
  }
  const { hard, soft } = detectConflicts(paths, args.task)
  if (hard.length > 0) {
    console.error('✗ 存在硬冲突：\n')
    hard.forEach((c) => console.error(formatConflict(c)))
    return 1
  }
  if (soft.length > 0) {
    console.warn('⚠ 存在软冲突：\n')
    soft.forEach((c) => console.warn(formatConflict(c)))
  } else {
    console.log('✓ 无冲突')
  }
  return 0
}

function cmdRelease() {
  const task = args.task
  if (!task || task === true) {
    console.error('✗ 缺少 --task')
    return 2
  }
  const f = leaseFile(task)
  if (!existsSync(f)) {
    console.error(`✗ 未找到租约：${task}`)
    return 1
  }
  unlinkSync(f)
  console.log(`✓ 已释放租约：${task}`)
  return 0
}

function cmdRenew() {
  const task = args.task
  if (!task || task === true) {
    console.error('✗ 缺少 --task')
    return 2
  }
  const f = leaseFile(task)
  if (!existsSync(f)) {
    console.error(`✗ 未找到租约：${task}`)
    return 1
  }
  const lease = JSON.parse(readFileSync(f, 'utf8'))
  const ttl = Number(args.ttl ?? lease.ttlMinutes ?? DEFAULT_TTL_MINUTES)
  const now = new Date()
  lease.renewedAt = now.toISOString()
  lease.expiresAt = new Date(now.getTime() + ttl * 60_000).toISOString()
  writeFileSync(f, `${JSON.stringify(lease, null, 2)}\n`, 'utf8')
  console.log(`✓ 已续租：${task} → ${new Date(lease.expiresAt).toLocaleString('zh-CN')}`)
  return 0
}

function cmdPrune() {
  const all = readLeases()
  let n = 0
  for (const l of all) {
    if (l.broken || isExpired(l)) {
      unlinkSync(l._file)
      console.log(`  已清理：${l.task}`)
      n++
    }
  }
  console.log(n === 0 ? '✓ 没有需要清理的租约' : `✓ 已清理 ${n} 个租约`)
  return 0
}

// ------------------------------------------------------------------
const COMMANDS = {
  status: cmdStatus,
  acquire: cmdAcquire,
  check: cmdCheck,
  release: cmdRelease,
  renew: cmdRenew,
  prune: cmdPrune,
}

if (!command || !COMMANDS[command]) {
  console.error('用法: node scripts/agent/lease.mjs <status|acquire|check|release|renew|prune> [选项]')
  console.error('')
  console.error('  status                        查看全部租约')
  console.error('  acquire --task T --paths P    取得租约（冲突则拒绝）')
  console.error('  check   --paths P             只检查不占用')
  console.error('  release --task T              释放')
  console.error('  renew   --task T [--ttl 分钟] 续租')
  console.error('  prune                         清理过期/损坏租约')
  process.exit(2)
}

process.exit(COMMANDS[command]())
