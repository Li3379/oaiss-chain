#!/usr/bin/env node
/**
 * JPA 实体与 Flyway 迁移的列类型一致性门禁。
 *
 * 背景：`carbon_coin_transaction.tx_type` 在 V1 建表时是 INT，V17 收紧成 TINYINT，
 * 但实体字段仍是 `Integer`。Hibernate 在 `ddl-auto: validate`（生产/CI 用）下
 * 拿「Integer → INTEGER」去比对实际列，发现是 TINYINT 就判定类型不符，
 * 直接让应用启动失败：
 *
 *   Schema-validation: wrong column type encountered in column [tx_type]
 *   found [tinyint], but expecting [integer]
 *
 * 这个缺陷的隐蔽性在于：本地开发普遍跑 `ddl-auto=none`（不校验），
 * 所以改完迁移照样能启动、测试全绿，直到部署才发现起不来。
 *
 * 本门禁扫描「迁移里出现 TINYINT 的列」与「实体里对应的 Java 字段类型」，
 * 要求实体显式声明 `columnDefinition = "TINYINT"`，否则报错。
 *
 * 用法：node scripts/gates/jpa-schema-verify.mjs
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..', '..')
const MIGRATION_DIR = join(ROOT, 'oaiss-chain-backend/src/main/resources/db/migration')
const ENTITY_DIR = join(ROOT, 'oaiss-chain-backend/src/main/java/com/oaiss/chain/entity')

/** 从迁移里提取「表.列 → 最终类型」，后者覆盖前者（V17 的 MODIFY 覆盖 V1 的建表） */
function collectColumnTypes() {
  const types = new Map() // `${table}.${column}` -> type
  if (!existsSync(MIGRATION_DIR)) return types

  const files = readdirSync(MIGRATION_DIR)
    .filter(f => f.endsWith('.sql'))
    // 必须按版本号数值排序，不能按字典序：字典序会把 "V1__" 排到 "V17__" 之后，
    // 于是 V1 建表里的 INT 反过来覆盖了 V17 的 TINYINT，门禁完全失效。
    .sort((a, b) => {
      const na = parseInt((a.match(/^V(\d+)/) || [])[1] || '0', 10)
      const nb = parseInt((b.match(/^V(\d+)/) || [])[1] || '0', 10)
      return na - nb
    })

  for (const file of files) {
    const sql = readFileSync(join(MIGRATION_DIR, file), 'utf8')

    // CREATE TABLE 中的列定义
    for (const t of sql.matchAll(/CREATE TABLE\s+`?(\w+)`?\s*\(([\s\S]*?)\n\)\s*ENGINE/gi)) {
      const table = t[1]
      for (const line of t[2].split('\n')) {
        const c = line.match(/^\s*`(\w+)`\s+([A-Za-z]+(?:\(\d+\))?)/)
        if (c) types.set(`${table}.${c[1]}`, c[2].toUpperCase())
      }
    }

    // ALTER TABLE ... ADD/MODIFY COLUMN
    //
    // 按分号切语句再逐条解析：早先用 /ALTER TABLE ... ;/ 一次性匹配，
    // 遇到一条 ALTER 里有多个 ADD/MODIFY 且中间夹注释时会被首个分号截断，
    // 导致后续 MODIFY 丢失（V17 的 tx_type: INT -> TINYINT 就是这样被漏掉的）。
    for (const stmt of sql.split(';')) {
      // 语句块前面常带 `--` 注释，因此不能用「以 ALTER 开头」来匹配，
      // 必须在任意位置定位 ALTER TABLE，否则整条 MODIFY 会被漏掉
      // （V17 的 tx_type: INT -> TINYINT 正是这样被跳过，导致门禁误判通过）。
      const a = stmt.match(/ALTER TABLE\s+`?(\w+)`?([\s\S]*)/i)
      if (!a) continue
      const table = a[1]
      for (const c of a[2].matchAll(/ADD COLUMN\s+`(\w+)`\s+([A-Za-z]+(?:\(\d+\))?)/gi)) {
        types.set(`${table}.${c[1]}`, c[2].toUpperCase())
      }
      for (const c of a[2].matchAll(/MODIFY COLUMN\s+`(\w+)`\s+([A-Za-z]+(?:\(\d+\))?)/gi)) {
        types.set(`${table}.${c[1]}`, c[2].toUpperCase())
      }
    }
  }
  return types
}

/** 实体类 → 表名 */
function collectEntities() {
  const map = new Map() // simpleClassName -> { table, fields: [{column, javaType, hasColumnDefinition}] }
  if (!existsSync(ENTITY_DIR)) return map

  for (const file of readdirSync(ENTITY_DIR).filter(f => f.endsWith('.java'))) {
    const src = readFileSync(join(ENTITY_DIR, file), 'utf8')
    const tm = src.match(/@Table\(name\s*=\s*"(\w+)"/)
    if (!tm) continue
    const fields = []
    // 允许 @Column 与字段之间夹杂注解、空行、以及 javadoc/行注释——
    // 早期版本只用 \s* 连接，遇到 /** ... */ 就断掉，导致大量字段被漏检。
    const re = /@Column\(([^)]*)\)[\s\S]{0,400}?private\s+([\w.<>]+)\s+(\w+)\s*[;=]/g
    for (const m of src.matchAll(re)) {
      const attrs = m[1]
      const javaType = m[2]
      const col = attrs.match(/name\s*=\s*"(\w+)"/)
      if (!col) continue
      // 防止跨到下一个字段：区间内不允许再出现 @Column
      const between = src.slice(m.index + m[0].indexOf(')'), m.index + m[0].length)
      if ((between.match(/@Column\(/g) || []).length > 1) continue
      fields.push({
        column: col[1],
        javaType,
        hasColumnDefinition: /columnDefinition\s*=/.test(attrs),
      })
    }
    map.set(file.replace('.java', ''), { table: tm[1], fields })
  }
  return map
}

function main() {
  console.log('=== JPA 实体 / Flyway 迁移 列类型一致性门禁 ===\n')

  const columnTypes = collectColumnTypes()
  const entities = collectEntities()

  if (columnTypes.size === 0 || entities.size === 0) {
    console.log('⚠ 未找到迁移或实体，跳过')
    return
  }

  const problems = []
  let checked = 0

  for (const [className, { table, fields }] of entities) {
    for (const field of fields) {
      const declared = columnTypes.get(`${table}.${field.column}`)
      if (!declared) continue
      checked++

      // 只关心窄整型与 Java Integer/Long 的组合：
      // Hibernate 对 Boolean<->TINYINT、LocalDateTime<->DATETIME 有默认映射，
      // 但 Integer -> INTEGER 是硬推断，列若是 TINYINT 必然校验失败。
      const narrowInt = /^TINYINT/.test(declared)
      const javaInt = /^(Integer|Long|int|long)$/.test(field.javaType)
      if (narrowInt && javaInt && !field.hasColumnDefinition) {
        problems.push(
          `${className}.${field.column} (${table}.${field.column}): ` +
          `迁移声明 ${declared}，实体 ${field.javaType} 未加 columnDefinition → ` +
          `Hibernate validate 会期望 INTEGER 而拒绝启动`,
        )
      }
    }
  }

  console.log(`已比对 ${checked} 个实体列\n`)

  if (problems.length > 0) {
    problems.forEach(p => console.log(`✗ ${p}`))
    console.log(`\n❌ ${problems.length} 处列类型不一致`)
    console.log('修复：在 @Column 上加 columnDefinition = "TINYINT"')
    process.exit(1)
  }

  console.log('✅ 所有窄整型列均显式声明了 columnDefinition')
}

main()
