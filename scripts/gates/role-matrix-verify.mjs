/**
 * OAISS CHAIN — 角色/权限语义一致性门禁 (role-matrix-verify)
 *
 * 解决的问题：既有 doc-verify.mjs 只校验「数字」（controller 数、端点数），
 * 无法发现「文档写的角色权限」与「代码 @PreAuthorize 实际授权」不一致。
 * 历史事故：《系统设计与架构规格书》权限矩阵把「执行撮合/碳币充值/用户管理等」判给第三方监管，
 * 而代码实际全部授权给 ADMIN，长期无人拦截。
 *
 * 校验项（方案 A 定稿口径，2026-10-21）：
 *   1) 碳报告上链（certify）归属：仅 REVIEWER / ADMIN，不得为其他角色
 *   2) 审核通过自动上链：reviewReport 中必须存在自动上链调用
 *   3) 第三方监管（THIRD_PARTY）不得持有任何写操作端点
 *   4) 管理员独占类端点（用户管理/签发证书/碳币充值）不得授予 THIRD_PARTY
 *
 * 用法：node scripts/gates/role-matrix-verify.mjs  （仓库根目录运行；失败退出码 1）
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, extname } from 'node:path'

const ROOT = process.cwd()
const CONTROLLER_DIR = join(ROOT, 'oaiss-chain-backend/src/main/java/com/oaiss/chain/controller')
const SERVICE_FILE = join(ROOT, 'oaiss-chain-backend/src/main/java/com/oaiss/chain/service/CarbonService.java')

const errors = []
const ok = []

function readControllers() {
  const out = []
  for (const f of readdirSync(CONTROLLER_DIR)) {
    if (!f.endsWith('.java')) continue
    out.push({ name: f, src: readFileSync(join(CONTROLLER_DIR, f), 'utf8') })
  }
  return out
}

const controllers = readControllers()

// --- 1) certify 端点授权角色 ---
const carbon = controllers.find((c) => c.name === 'CarbonController.java')
if (!carbon) {
  errors.push('未找到 CarbonController.java')
} else {
  const lines = carbon.src.split(/\r?\n/)
  const idx = lines.findIndex((l) => l.includes('@PostMapping("/certify")'))
  if (idx < 0) {
    errors.push('CarbonController 中未找到 @PostMapping("/certify")')
  } else {
    const window = lines.slice(idx, idx + 12).join('\n')
    // 方案 A：REVIEWER 为主体，ADMIN 兜底
    if (!/hasRole\('REVIEWER'\) or hasRole\('ADMIN'\)/.test(window)) {
      errors.push('certify 授权不符方案 A：期望 hasRole(REVIEWER) or hasRole(ADMIN)，实际窗口=' + window.replace(/\s+/g, ' ').slice(0, 160))
    } else {
      ok.push('certify 授权 = REVIEWER 或 ADMIN（方案 A 补救入口）')
    }
  }
}

// --- 2) 审核通过自动上链 ---
const svc = readFileSync(SERVICE_FILE, 'utf8')
if (!/autoCommitToChain\(/.test(svc)) {
  errors.push('CarbonService 缺少 autoCommitToChain 调用——审核通过自动上链链路断裂（方案 A 核心行为）')
} else {
  const reviewIdx = svc.indexOf('public CarbonReportResponse reviewReport')
  const body = svc.slice(reviewIdx, reviewIdx + 3000)
  if (!body.includes('autoCommitToChain(')) {
    errors.push('reviewReport 内未调用 autoCommitToChain——审核通过不会自动上链')
  } else {
    ok.push('reviewReport 内调用 autoCommitToChain（审核通过自动上链）')
  }
}

// --- 3) THIRD_PARTY 不得持有「业务写」端点 ---
//
// 判定口径（2026-10-21 修正）：
//   第三方监管的定位是「穿透式只读核查」，禁止的是**影响他人或全局业务状态**的写操作
//   （上链、撮合、发证、改配额、改信誉分等）。
//   「操作自己账号资源」的自助端点（如 /signature/keypair 管理本人生成的密钥对）
//   不属于业务写操作，监管持有自己的密钥、对他人报告验签是其本职能力。
//
// 历史误报教训：早期规则把所有 POST/PUT/DELETE 一律判为违规，
// 会把 /signature/keypair（自助密钥管理）误判为越权，属规则过宽。
const WRITE = /@(Post|Put|Delete|Patch)Mapping/
// 注意：此处匹配的是 (控制器文件名 + 方法级路径)，不含 @RequestMapping 基路径，
// 故 hint 用方法级片段（如 '/keypair'）而非 '/signature/keypair'。
const SELF_SERVICE_HINTS = [
  '/keypair',        // 管理当前用户自己的密钥对（生成/查询/撤销）
  '/sign',           // 用本人私钥签名
  '/verify',         // 核验他人报告签名（监管本职）
  '/encrypt',
  '/decrypt',
  '/logout',
  '/stay-time',
  '/notification',
  '/read',           // 标记本人通知已读
  '/read-all',
]
let thirdPartyWrite = []
for (const c of controllers) {
  const lines = c.src.split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    if (!WRITE.test(lines[i])) continue
    const pathMatch = lines[i].match(/@(Post|Put|Delete|Patch)Mapping\("([^"]*)"\)/)
    const path = pathMatch ? pathMatch[2] : '?'
    if (SELF_SERVICE_HINTS.some((h) => (c.name + path).includes(h))) continue
    let pre = null
    for (let j = i + 1; j < Math.min(i + 25, lines.length); j++) {
      const m = lines[j].match(/@PreAuthorize\("([^"]+)"\)/)
      if (m) pre = m[1]
      if (/^\s*public\s+[\w<>,\[\] ]+\s+\w+\s*\(/.test(lines[j])) break
    }
    if (pre && pre.includes('THIRD_PARTY')) thirdPartyWrite.push(c.name + ' ' + path + ' -> ' + pre)
  }
}
if (thirdPartyWrite.length) {
  errors.push('第三方监管持有业务写操作端点（应仅保留只读+自助）：\n     - ' + thirdPartyWrite.join('\n     - '))
} else {
  ok.push('第三方监管未持有业务写端点（只读核查 + 本人密钥自助，符合 L1/L2）')
}

// --- 4) 管理员独占端点不得授予 THIRD_PARTY ---
const ADMIN_ONLY_HINTS = ['/users', '/enterprise-admission', '/reviewer-qualification', '/recharge']
let leaked = []
for (const c of controllers) {
  const lines = c.src.split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/@(Get|Post|Put|Delete|Patch)Mapping\("([^"]*)"\)/)
    if (!m) continue
    const path = m[2]
    if (!ADMIN_ONLY_HINTS.some((h) => path.includes(h))) continue
    let pre = null
    for (let j = i + 1; j < Math.min(i + 25, lines.length); j++) {
      const pm = lines[j].match(/@PreAuthorize\("([^"]+)"\)/)
      if (pm) pre = pm[1]
      if (/^\s*public\s+[\w<>,\[\] ]+\s+\w+\s*\(/.test(lines[j])) break
    }
    if (pre && pre.includes('THIRD_PARTY')) leaked.push(c.name + ' ' + path + ' -> ' + pre)
  }
}
if (leaked.length) {
  errors.push('管理员独占端点被授予第三方监管：\n     - ' + leaked.join('\n     - '))
} else {
  ok.push('管理员独占端点（用户管理/准入签发/资格证/充值）未泄漏给第三方监管')
}

console.log('=== OAISS CHAIN 角色/权限语义门禁 ===')
console.log('')
for (const o of ok) console.log('✓ ' + o)
if (errors.length) {
  console.log('')
  console.log('✗ 校验失败（' + errors.length + ' 项）：')
  for (const e of errors) console.log('   - ' + e)
  console.log('')
  console.log('❌ 角色权限语义漂移——请同步代码与《系统设计与架构规格书》权限矩阵')
  process.exit(1)
}
console.log('')
console.log('✅ 全部通过 — 角色权限语义与方案 A 定稿一致')
