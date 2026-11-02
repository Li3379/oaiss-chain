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
  // 窗口不能写死字数：注释越写越长会把调用点挤出窗口，导致门禁红在正确代码上。
  // 改为截到**下一个方法声明**为止——那才是方法体的真实边界。
  const nextMethodIdx = svc.indexOf('    private ', reviewIdx + 1)
  const bodyEnd = nextMethodIdx > reviewIdx ? nextMethodIdx : reviewIdx + 8000
  const body = svc.slice(reviewIdx, bodyEnd)
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
  // 监管线索的「处置标记」：给系统产出的某条线索打上"监管已看过并处理"的印记。
  // 它**不改变任何企业或交易状态**（不冻结、不扣分、不下市），
  // 因此不属于「影响他人或全局业务状态的写操作」。
  // 真正影响企业状态的动作（冻结/下市）仍归管理员，监管只能提请。
  // 注意：判定用的是「控制器文件名 + 方法级路径」（不含 @RequestMapping 基路径），
  // 所以这里写 '/findings' 而不是 '/regulatory/findings'。
  '/findings',  // 见 RegulatoryFindingController#resolveFinding
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

// --- 3b) 业务写端点必须显式声明授权角色（无注解 = 任何登录用户可调） ---
//
// 历史盲区（2026-10-31 补）：上面第 3 项只扫描「方法级 @PreAuthorize 含 THIRD_PARTY」的写端点，
// 而放行判定的真正来源是三级回落：类级 @PreAuthorize → 方法级 → SecurityConfig.anyRequest().authenticated()。
// 于是「既无类级注解、也无方法级注解」的业务写端点等于对任何登录角色（含 THIRD_PARTY）开放，
// 却永远不会出现在 thirdPartyWrite 里。实测漏检的隐式写面：
//   POST /emission/ratings（任意登录用户可覆盖任意企业年度评级）
//   POST /copilot/*（3 个）
//   DELETE /file、/file/batch（裁决在服务层，注解层面无约束）
//
// 判定口径：业务写端点必须在「类级或方法级」任一处出现 @PreAuthorize，否则报错。
// 自助类（本人账号资源）端点豁免由 SELF_SERVICE_HINTS 统一承担，与第 3 项口径保持一致。
// 注意：此处匹配的是 (控制器文件名 + 方法级路径)，不含 @RequestMapping 基路径，
// 故 hint 用方法级片段（如 '/keypair'）而非 '/signature/keypair'。
const controllersWithClassAuth = new Set()
for (const c of controllers) {
  const lines = c.src.split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    // 类级注解出现在 public class 声明之前
    if (/^\s*public\s+class\s+\w+/.test(lines[i])) break
    if (/@PreAuthorize\(/.test(lines[i])) {
      controllersWithClassAuth.add(c.name)
      break
    }
  }
}

// 公开端点（SecurityConfig permitAll，无需 @PreAuthorize）
// 口径与 SecurityConfig.java:66-75 的 requestMatchers(...).permitAll() 保持一致。
const PUBLIC_ENDPOINTS = [
  '/login',
  '/register',
  '/captcha',
  '/refresh',
  '/check-ip',
  '/sms/send',
  '/email/send',
]

// 自助端点（仅操作当前登录者的本人资源，无跨账号影响面）——按控制器+路径片段豁免。
// 依据：这些端点内部均以 currentUser 为准，不接受他人 ID。
const SELF_SERVICE_WRITE_HINTS = [
  'UserController.java /profile',
  'UserController.java /password',
  'AuthController.java /password',
  'WebVitalsController.java /report', // 前端性能埋点，仅写指标日志，无业务状态
]

// 服务层兜底豁免：授权由 Service 的归属校验强制，而非注解。
// 例：CarbonNeutralProjectController 的 9 个写端点全部经
// CarbonNeutralProjectService.validateOwner()（:135/:168/:220/:242/:299/:322/:341/:385），
// 以「企业必须关联到当前用户」为前置：第三方监管与管理员均无企业关联 →
// 抛 BusinessException(3001)，从未能通过这些端点写入。
// 注意：这是「设计上的服务层兜底」，不是「忘了加注解」。若后续把某个方法的
// validateOwner 移除，此豁免即失效，必须同时补回注解。
const SERVICE_LAYER_GUARDED = [
  'CarbonNeutralProjectController.java',
]

/**
 * 从 @XxxMapping 所在行向后找到方法签名行，返回其间的全部注解文本。
 * 不设行数上限：Swagger @Operation/@ApiResponses 块常常超 25 行，
 * 早期写死 25 行窗口会把它后面的 @PreAuthorize 漏掉，误报成"无授权"。
 */
function annotationsBeforeSignature(lines, mappingIdx) {
  const collected = []
  for (let j = mappingIdx + 1; j < lines.length; j++) {
    if (/^\s*public\s+[\w<>,\[\] ]+\s+\w+\s*\(/.test(lines[j])) break
    collected.push(lines[j])
  }
  return collected.join('\n')
}

let implicitWrites = []
for (const c of controllers) {
  const lines = c.src.split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    if (!WRITE.test(lines[i])) continue
    const pathMatch = lines[i].match(/@(Post|Put|Delete|Patch)Mapping\("([^"]*)"\)/)
    const path = pathMatch ? pathMatch[2] : '?'
    if (SELF_SERVICE_HINTS.some((h) => (c.name + path).includes(h))) continue
    if (PUBLIC_ENDPOINTS.some((p) => path === p || path.startsWith(p + '/')) &&
        (/^(Auth|Captcha)Controller\.java$/.test(c.name))) continue
    if (SELF_SERVICE_WRITE_HINTS.some((h) => (c.name + ' ' + path) === h)) continue
    if (SERVICE_LAYER_GUARDED.includes(c.name)) continue
    const block = annotationsBeforeSignature(lines, i)
    const methodPre = block.match(/@PreAuthorize\("([^"]+)"\)/)
    const hasClassAuth = controllersWithClassAuth.has(c.name)
    if (!methodPre && !hasClassAuth) implicitWrites.push(c.name + ' ' + path)
  }
}
if (implicitWrites.length) {
  errors.push(
    '业务写端点缺少显式 @PreAuthorize（类级与方法级均无 → 任何登录角色可调用）：\n     - ' +
      implicitWrites.join('\n     - ')
  )
} else {
  ok.push('业务写端点均在类级或方法级显式声明授权角色（无隐式写面）')
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
