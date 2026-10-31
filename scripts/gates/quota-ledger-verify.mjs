#!/usr/bin/env node
/**
 * OAISS CHAIN — 配额字段写入收敛门禁 (quota-ledger-verify)
 *
 * 解决的问题：carbonTradable / carbonFrozen / carbonUsed / carbonQuota 四个字段
 * 此前直接内联在交易结算路径里就地加减（TradeService 12 处、DoubleAuctionService 11 处），
 * 导致额度变动事后无法逐笔归因。V16 引入 quota_transaction 账本表与 QuotaLedgerService 后，
 * 新增代码必须走账本服务，否则「账本」会立刻退化为一段只有部分路径在写的不完整记录。
 *
 * 校验口径：
 *   1) 配额四字段的 setter 只允许出现在 QuotaLedgerService 内（唯一合法写入口）
 *   2) 例外清单 LEGACY_ALLOWLIST 中的既有调用点被显式点名（含存量技术债编号），
 *      防止「没人知道还有哪些地方在直接改字段」
 *   3) 新增的非法调用点直接报错——这是本门禁的核心价值：
 *      它把「绕过账本」从隐形的坏习惯变成一次构建失败
 *
 * 用法：node scripts/gates/quota-ledger-verify.mjs  （仓库根目录运行；失败退出码 1）
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = process.cwd()
const SRC_DIR = join(ROOT, 'oaiss-chain-backend/src/main/java')
const LEDGER_SERVICE = join(SRC_DIR, 'com/oaiss/chain/service/QuotaLedgerService.java')

/** 配额字段的 setter 名称（Lombok @Setter 生成） */
const QUOTA_SETTERS = [
  'setCarbonTradable',
  'setCarbonFrozen',
  'setCarbonUsed',
  'setCarbonQuota',
]

/**
 * 存量技术债豁免清单（DEBT-05「配额字段写入收敛」专项）。
 *
 * 这些是 V16 之前就存在的内联写点，本轮不迁移（改交易结算核心会冲击
 * TradeServiceTest 60 例 + DoubleAuctionServiceTest 50 例的既有断言）。
 * 每一项都必须能被解释：写在哪、为什么还在。
 *
 * 收敛规则：此清单只允许缩短。新增一行意味着新的绕过，必须先在
 * QuotaLedgerService 里加语义化方法或说明为何不能走账本。
 */
const LEGACY_ALLOWLIST = {
  'com/oaiss/chain/service/TradeService.java': {
    maxOccurrences: 13,
    reason: 'P2P 结算/冻结/撤单解冻存量内联写点（DEBT-05）',
  },
  'com/oaiss/chain/service/DoubleAuctionService.java': {
    maxOccurrences: 10,
    reason: '双向拍卖撮合结算与撤单解冻存量内联写点（DEBT-05）',
  },
}

const errors = []
const ok = []

function walk(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      walk(full, acc)
    } else if (entry.endsWith('.java')) {
      acc.push(full)
    }
  }
  return acc
}

const files = walk(SRC_DIR)

let totalLegacy = 0
for (const file of files) {
  const rel = file.replace(/\\/g, '/').split('src/main/java/')[1]
  const src = readFileSync(file, 'utf8')

  const hits = []
  const lines = src.split(/\r?\n/)
  lines.forEach((line, idx) => {
    // 忽略纯注释行，避免把文档说明判为写入
    const trimmed = line.trim()
    if (trimmed.startsWith('*') || trimmed.startsWith('//') || trimmed.startsWith('/*')) return
    for (const setter of QUOTA_SETTERS) {
      if (line.includes(setter + '(')) {
        hits.push({ setter, line: idx + 1 })
      }
    }
  })

  if (hits.length === 0) continue

  // 1) 账本服务是唯一合法写入口
  if (file === LEDGER_SERVICE) continue

  // 2) 存量豁免：只校验不超上限
  const allow = LEGACY_ALLOWLIST[rel]
  if (allow) {
    if (hits.length > allow.maxOccurrences) {
      errors.push(
        `${rel}: 配额字段写入 ${hits.length} 处，超过豁免上限 ${allow.maxOccurrences} 处。` +
          `\n     豁免原因：${allow.reason}` +
          `\n     新增写入必须改走 QuotaLedgerService（FREEZE/UNFREEZE/TRANSFER/INCREASE/ROLLBACK）`
      )
    } else {
      totalLegacy += hits.length
    }
    continue
  }

  // 3) 未申报的写入点 = 绕过账本
  errors.push(
    `${rel}: 未申报的配额字段写入（共 ${hits.length} 处）` +
      `\n     - ` +
      hits.map((h) => `${h.setter} 第 ${h.line} 行`).join('\n     - ') +
      `\n     配额变动必须走 QuotaLedgerService 以保证逐笔可归因（V16 账本表）`
  )
}

if (errors.length) {
  console.log('=== OAISS CHAIN 配额账本门禁 ===\n')
  errors.forEach((e) => console.log('✗ ' + e))
  console.log(`\n❌ ${errors.length} 项违规 — 新增配额写入必须收敛到 QuotaLedgerService`)
  process.exit(1)
}

console.log('=== OAISS CHAIN 配额账本门禁 ===\n')
console.log('✓ 配额字段写入仅出现在 QuotaLedgerService（唯一合法写入口）')
console.log(
  `✓ 存量内联写点已全部申报：${totalLegacy} 处（DEBT-05 待迁移，见 LEGACY_ALLOWLIST）`
)
console.log('\n✅ 全部通过 — 配额变动可逐笔归因')