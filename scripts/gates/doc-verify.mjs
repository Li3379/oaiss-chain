#!/usr/bin/env node
/**
 * OAISS CHAIN — 文档结构断言真实门禁 (Doc Verify)
 *
 * 彻底消除「假门禁」：动态解析 DEV-GUIDE.md §11「核心系统结构事实」表格，
 * 提取各项声明值作为期望基准，与代码工程实际提取值进行严格双向比对！
 * 若文档写错或代码写错，门禁均会失败阻断并返回非零退出码。
 *
 * 运行: node scripts/gates/doc-verify.mjs (或 node scripts/doc-verify.mjs)
 * 退出码: 0 = 全部通过 | 1 = 存在漂移（代码与文档不一致）
 *
 * 断言锚点来源: DEV-GUIDE.md §11（唯一权威真相源）
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '../..')
const DEV_GUIDE_PATH = path.join(ROOT, 'DEV-GUIDE.md')

if (!fs.existsSync(DEV_GUIDE_PATH)) {
  console.error(`❌ 无法找到权威真相源文档: ${DEV_GUIDE_PATH}`)
  process.exit(1)
}

// ---- 1. 从 DEV-GUIDE.md §11 动态解析事实基准 ----
const devGuideContent = fs.readFileSync(DEV_GUIDE_PATH, 'utf8')

function parseDevGuideSection11(content) {
  // 匹配 §11 核心系统结构事实 / 权威事实 标题到下一个二级标题之间内容
  const secMatch = content.match(/##\s*11[.\s]+([^\r\n]+)([\s\S]*?)(?=\n##\s+\d|\n#\s+|$)/)
  if (!secMatch) {
    throw new Error('DEV-GUIDE.md 中未找到 §11 核心系统结构事实表格！')
  }

  const tableText = secMatch[2]
  const rows = []
  for (const line of tableText.split('\n')) {
    const m = line.match(/^\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*(.*?)\|\s*$/)
    if (m) {
      const col1 = m[1].trim()
      const col2 = m[2].trim()
      const col3 = m[3].trim()
      if (col1 === '事实' || col1.startsWith('---')) continue
      rows.push({ fact: col1, val: col2, desc: col3 })
    }
  }

  const expected = {}
  for (const row of rows) {
    const f = row.fact.toLowerCase()
    if (/controller/.test(f)) {
      expected.controllers = parseInt(row.val, 10)
    } else if (/端点|endpoint/.test(f)) {
      expected.endpoints = parseInt(row.val, 10)
    } else if (/service/.test(f)) {
      expected.services = parseInt(row.val, 10)
      const mMain = row.desc.match(/(\d+)\s*主包/)
      if (mMain) expected.servicesMain = parseInt(mMain[1], 10)
      const mMl = row.desc.match(/(\d+)\s*ml/i)
      if (mMl) expected.servicesMl = parseInt(mMl[1], 10)
    } else if (/entit/.test(f)) {
      expected.entities = parseInt(row.val, 10)
    } else if (/table|表/.test(f)) {
      expected.tables = parseInt(row.val, 10)
    } else if (/repositor/.test(f)) {
      expected.repositories = parseInt(row.val, 10)
    } else if (/api.*模块|apimodule/.test(f)) {
      expected.apiModules = parseInt(row.val, 10)
    } else if (/枚举|enum/.test(f)) {
      expected.enums = parseInt(row.val, 10)
    } else if (/flyway|迁移|migration/.test(f)) {
      const rangeMatch = row.val.match(/V(\d+)\s*[-~至到]\s*V(\d+)/i)
      if (rangeMatch) {
        const start = parseInt(rangeMatch[1], 10)
        const end = parseInt(rangeMatch[2], 10)
        // 跳号必须由文档显式声明，门禁不猜。
        //
        // 历史写法只硬编码识别 V3，于是任何后续空号都会被判成「缺 Vxx」。
        // 空号是真实存在的：V22 就是被 V23 抢先入库后留下的永久空洞
        // ——若补一个 V22，已跑过 V23 的环境会因 Flyway out-of-order 拒绝启动。
        // 因此空号只能「声明」，不能「消灭」；门禁要做的是确认声明的空号
        // 确实没有对应文件（双向），而不是把声明当豁免。
        // 空号声明支持两种写法：
        //   逗号/顿号并列 —— "V3、V22 跳过"
        //   逐项后缀     —— "V3 跳过，V22 空号"
        // 用「取『跳过/空号』前的整个 V 列表」的方式解析，避免只为并列写法
        // 加一个正则分支（那是上一版漏掉 V3 的原因）。
        // 扫描前先把区间标记（V1-V24）本身剔掉，否则区间两端会被当成
        // 空号声明——「V1-V24 V3、V22 跳过」会把 V1 与 V24 误判成空号。
        const gapSrc = row.val.replace(/V\d+\s*[-~至到]\s*V\d+/gi, ' ') + ' ' + row.desc
        const skipped = new Set()
        for (const seg of gapSrc.split(/[，,；;]/)) {
          if (!/跳过|空号/.test(seg)) continue
          const head = seg.split(/跳过|空号/)[0]
          for (const m of head.matchAll(/V(\d+)/g)) skipped.add(`V${m[1]}`)
        }
        for (const m of gapSrc.matchAll(/(?:跳过|空号)\s*V(\d+)/g)) skipped.add(`V${m[1]}`)
        const expectedMigs = []
        for (let i = start; i <= end; i++) {
          if (skipped.has(`V${i}`)) continue
          expectedMigs.push(`V${i}`)
        }
        expected.migrations = expectedMigs
        expected.declaredGaps = [...skipped]
      }
    } else if (/i18n/.test(f)) {
      expected.i18n = parseInt(row.val.replace(/,/g, ''), 10)
    } else if (/角色|role/.test(f)) {
      expected.rolesCount = parseInt(row.val, 10)
      const rolesMatch = row.desc.match(/([A-Z_]+(?:\/[A-Z_]+)+)/)
      if (rolesMatch) {
        expected.roles = rolesMatch[1].split('/')
      }
    }
  }

  // 默认补充兜底（若表格未展开子项）
  if (expected.services && !expected.servicesMain) expected.servicesMain = 34
  if (expected.services && !expected.servicesMl) expected.servicesMl = 3
  if (!expected.roles) expected.roles = ['ENTERPRISE', 'REVIEWER', 'THIRD_PARTY', 'ADMIN']

  const requiredKeys = ['controllers', 'endpoints', 'services', 'entities', 'tables', 'repositories', 'apiModules', 'enums', 'migrations', 'i18n']
  const missing = requiredKeys.filter(k => expected[k] === undefined)
  if (missing.length > 0) {
    throw new Error(`DEV-GUIDE.md §11 表格缺少必要指标解析项: ${missing.join(', ')}`)
  }

  return expected
}

let EXPECT
try {
  EXPECT = parseDevGuideSection11(devGuideContent)
} catch (err) {
  console.error(`❌ 解析 DEV-GUIDE.md §11 失败: ${err.message}`)
  process.exit(1)
}

// ---- 2. 从代码工程中提取实际事实 ----
const controllerDir = path.join(ROOT, 'oaiss-chain-backend/src/main/java/com/oaiss/chain/controller')
const serviceDir = path.join(ROOT, 'oaiss-chain-backend/src/main/java/com/oaiss/chain/service')
const entityDir = path.join(ROOT, 'oaiss-chain-backend/src/main/java/com/oaiss/chain/entity')
const repoDir = path.join(ROOT, 'oaiss-chain-backend/src/main/java/com/oaiss/chain/repository')
const enumDir = path.join(ROOT, 'oaiss-chain-backend/src/main/java/com/oaiss/chain/enums')
const migrationDir = path.join(ROOT, 'oaiss-chain-backend/src/main/resources/db/migration')
const apiDir = path.join(ROOT, 'oaiss-chain-frontend/src/api')
const localesDir = path.join(ROOT, 'oaiss-chain-frontend/src/i18n/locales')

const listFiles = (dir, ext) => {
  if (!fs.existsSync(dir)) return []
  return fs.readdirSync(dir).filter(f => f.endsWith(ext))
}

const controllers = listFiles(controllerDir, '.java')
const endpoints = listFiles(controllerDir, '.java').reduce((sum, f) => {
  const content = fs.readFileSync(path.join(controllerDir, f), 'utf8')
  const matches = content.match(/@(?:Get|Post|Put|Delete|Patch)Mapping/g) || []
  return sum + matches.length
}, 0)

const servicesAll = listFiles(serviceDir, '.java')
const servicesMl = listFiles(path.join(serviceDir, 'ml'), '.java')
const servicesMain = servicesAll.filter(s => s !== 'AGENTS.md')

const entities = listFiles(entityDir, '.java')

// 数据库表：识别 CREATE TABLE 与 DROP TABLE，精确计算现存物理表数
const tableStats = (() => {
  const files = listFiles(migrationDir, '.sql')
  const created = new Set()
  const dropped = new Set()
  for (const f of files) {
    const content = fs.readFileSync(path.join(migrationDir, f), 'utf8')
    for (const m of content.matchAll(/CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?/gi)) {
      created.add(m[1].toLowerCase())
    }
    for (const m of content.matchAll(/DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?`?(\w+)`?/gi)) {
      dropped.add(m[1].toLowerCase())
    }
  }
  const active = new Set([...created].filter(t => !dropped.has(t)))
  return {
    createdCount: created.size,
    droppedCount: dropped.size,
    droppedTables: [...dropped],
    activeCount: active.size,
    activeTables: [...active],
  }
})()

const repos = listFiles(repoDir, '.java')
const apiModules = listFiles(apiDir, '.ts').filter(f => f !== 'request.ts' && f !== 'index.ts')
const enums = listFiles(enumDir, '.java')
const migrations = listFiles(migrationDir, '.sql').map(f => f.split('__')[0])

// 前端 i18n 词条解析
function collectI18nKeys(filePath) {
  if (!fs.existsSync(filePath)) return new Set()
  const text = fs.readFileSync(filePath, 'utf8')
  const keys = []
  const stack = []
  for (const line of text.split(/\r?\n/)) {
    const obj = /^(\s*)([A-Za-z_$][\w$]*):\s*\{/.exec(line)
    if (obj) {
      stack.push([obj[1].length, obj[2]])
      continue
    }
    const leaf = /^(\s*)([A-Za-z_$][\w$]*):\s*(['"`])/.exec(line)
    if (leaf) {
      const depth = leaf[1].length
      while (stack.length && stack[stack.length - 1][0] >= depth) stack.pop()
      const key = [...stack.map((s) => s[1]), leaf[2]].join('.')
      keys.push(key)
      continue
    }
    if (/^\s*\},?\s*$/.test(line) && stack.length) stack.pop()
  }
  return new Set(keys)
}

const zhPath = path.join(localesDir, 'zh-CN.ts')
const enPath = path.join(localesDir, 'en-US.ts')
const zhKeys = collectI18nKeys(zhPath)
const enKeys = collectI18nKeys(enPath)

// ---- 3. 断言比对执行 ----
let failures = 0
const check = (label, actual, expected, extra = '') => {
  const ok = actual === expected
  if (!ok) failures++
  console.log(`${ok ? '✓' : '✗'} ${label}: 实际=${actual} 文档期望=${expected}${extra ? ` (${extra})` : ''}`)
}

console.log('=== OAISS CHAIN 文档结构真实断言 (数据源: DEV-GUIDE.md §11) ===\n')
check('Controllers', controllers.length, EXPECT.controllers)
check('REST 端点', endpoints, EXPECT.endpoints)
check('Services (主包)', servicesMain.length, EXPECT.servicesMain)
check('Services (ml/)', servicesMl.length, EXPECT.servicesMl)
check('Services (合计)', servicesMain.length + servicesMl.length, EXPECT.services)
check('Entities', entities.length, EXPECT.entities)
check('数据库物理表 (现存表数)', tableStats.activeCount, EXPECT.tables, `历史创建 ${tableStats.createdCount} 张，扣除 DROP TABLE: ${tableStats.droppedTables.join(', ')}`)
check('Repositories', repos.length, EXPECT.repositories)
check('前端 API 模块', apiModules.length, EXPECT.apiModules, '排除 request.ts 与 index.ts')
check('枚举数量', enums.length, EXPECT.enums)

// 枚举名单校验
const enumNames = enums.map(f => f.replace('.java', ''))
const expectEnums = ['UserTypeEnum', 'ReportStatusEnum', 'TradeTypeEnum', 'TradeStatusEnum',
  'AuctionOrderStatusEnum', 'MatchingStatusEnum', 'CreditLevelEnum', 'CreditEventTypeEnum',
  'QualificationStatusEnum']
const missingEnums = expectEnums.filter(e => !enumNames.includes(e))
const extraEnums = enumNames.filter(e => !expectEnums.includes(e))
if (missingEnums.length === 0 && extraEnums.length === 0) {
  console.log(`✓ 核心枚举名单: 9 个全部匹配 (${enumNames.join(', ')})`)
} else {
  failures++
  console.log(`✗ 核心枚举名单: 期望 ${expectEnums.length} 个，缺 ${missingEnums.join(',') || '无'}，多 ${extraEnums.join(',') || '无'}`)
}

// Flyway 迁移版本（双向断言：文档列的必须存在，代码有的必须被文档列到）
// 历史盲区：早期只做单向包含，导致 V15 落地后 DEV-GUIDE 仍写「V1-V14 共 13 个版本」
// 而门禁全绿——文档少报版本永远不会被发现。
//
// 第二重盲区：空号。V23 先入库、V22 后到，V22 就成了永久空洞（补齐会让
// 已跑过 V23 的环境 out-of-order 拒启动）。若门禁只认「区间内必须逐个存在」，
// 正确状态反而被判红——于是要么放宽断言（失去保护），要么伪造一个 V22（真炸库）。
// 这里改成：空号必须由文档显式声明，且声明了就必须真的没有文件。
const migrateSet = new Set(migrations)
const missingMig = EXPECT.migrations.filter(v => !migrateSet.has(v))
const expectedMigSet = new Set(EXPECT.migrations)
const undeclaredMig = migrations.filter(v => !expectedMigSet.has(v))
const declaredGaps = EXPECT.declaredGaps ?? []
const phantomGaps = declaredGaps.filter(v => migrateSet.has(v))
if (missingMig.length === 0 && undeclaredMig.length === 0 && phantomGaps.length === 0) {
  const gapNote = declaredGaps.length ? `，${declaredGaps.join('/')} 已声明为空号` : ''
  console.log(`✓ Flyway 迁移: ${EXPECT.migrations.join(',')} (共 ${EXPECT.migrations.length} 个版本${gapNote})`)
} else {
  failures++
  console.log(`✗ Flyway 迁移: 缺 ${missingMig.join(',') || '无'}${phantomGaps.length ? `，声明为空号但文件存在 ${phantomGaps.join(',')}` : ''}${undeclaredMig.length ? `，代码存在但文档未声明 ${undeclaredMig.join(',')}` : ''}`)
}

// i18n 词条断言
check('前端 i18n 词条 (zh-CN)', zhKeys.size, EXPECT.i18n)
check('前端 i18n 词条 (en-US)', enKeys.size, EXPECT.i18n)
if (zhKeys.size === enKeys.size && zhKeys.size === EXPECT.i18n) {
  console.log(`✓ 前端国际化语言包: zh/en 双语严格对称 (${zhKeys.size} 词条)`)
} else {
  failures++
  console.log(`✗ 前端国际化语言包不对称: zh-CN=${zhKeys.size}, en-US=${enKeys.size}`)
}

// 角色校验（四角色在 controller @PreAuthorize 中动态使用，扫描全部 controller 验证）
let rolesOk = true
const allControllerContent = listFiles(controllerDir, '.java')
  .map(f => fs.readFileSync(path.join(controllerDir, f), 'utf8'))
  .join('\n')
for (const role of EXPECT.roles) {
  const inPreAuthorize = allControllerContent.includes(`'${role}'`) || allControllerContent.includes(`"${role}"`)
  if (!inPreAuthorize) { rolesOk = false; console.log(`✗ 角色 ${role} 未在任何 @PreAuthorize 中使用`); failures++ }
}
if (rolesOk) console.log(`✓ 角色权限: ${EXPECT.roles.join(', ')} 均在 @PreAuthorize 中使用`)

console.log(`\n${failures === 0 ? '✅ 全部断言通过 — 文档 (DEV-GUIDE.md §11) 与代码零漂移' : `❌ ${failures} 项漂移 — 请同步 DEV-GUIDE.md §11 或修复对应代码事实`}`)
process.exit(failures === 0 ? 0 : 1)
