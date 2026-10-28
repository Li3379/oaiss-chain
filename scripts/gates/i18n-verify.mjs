#!/usr/bin/env node
/**
 * i18n 键引用门禁 (i18n-verify)
 *
 * 解决的问题：既有"zh/en 键集合相等"检查无法发现「两侧同缺」的键——
 * 组件引用了不存在的键时 vue-i18n 会直出键名（如 notification.title），
 * 而键集合对比仍然通过。本脚本以代码引用为基准反向校验。
 *
 * 校验项：
 *   1) 代码中 t('a.b') / t("a.b") / $t('a.b') 引用的键，必须在 zh-CN 与 en-US 同时存在
 *   2) zh-CN 与 en-US 键集合完全相等（无单侧键）
 *   3) 同一语言内无重复键
 *
 * 用法：node scripts/i18n-verify.mjs   （仓库根目录运行；失败退出码 1）
 */

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, extname } from 'node:path'

const ROOT = process.cwd()
const LOCALES = {
  'zh-CN': 'oaiss-chain-frontend/src/i18n/locales/zh-CN.ts',
  'en-US': 'oaiss-chain-frontend/src/i18n/locales/en-US.ts',
}
const SRC_DIRS = ['oaiss-chain-frontend/src']
const EXCLUDE_DIRS = ['node_modules', 'dist', '__tests__', 'tests']

/** 解析 TS 对象字面量为点号键集合（仅 1 级缩进的对象与叶子，与项目 i18n 结构一致） */
function collectKeys(file) {
  const text = readFileSync(join(ROOT, file), 'utf8')
  const keys = []
  const dupes = []
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
      if (keys.includes(key)) dupes.push(key)
      keys.push(key)
      continue
    }
    if (/^\s*\},?\s*$/.test(line) && stack.length) stack.pop()
  }
  return { keys: new Set(keys), dupes }
}

function walk(dir, out = []) {
  for (const entry of readdirSync(join(ROOT, dir))) {
    if (EXCLUDE_DIRS.includes(entry)) continue
    const rel = join(dir, entry)
    const st = statSync(join(ROOT, rel))
    if (st.isDirectory()) walk(rel, out)
    else if (['.vue', '.ts', '.js'].includes(extname(entry))) out.push(rel)
  }
  return out
}

const errors = []
const zh = collectKeys(LOCALES['zh-CN'])
const en = collectKeys(LOCALES['en-US'])

if (zh.dupes.length) errors.push(`zh-CN 重复键: ${zh.dupes.join(', ')}`)
if (en.dupes.length) errors.push(`en-US 重复键: ${en.dupes.join(', ')}`)

const onlyZh = [...zh.keys].filter((k) => !en.keys.has(k))
const onlyEn = [...en.keys].filter((k) => !zh.keys.has(k))
if (onlyZh.length) errors.push(`仅 zh-CN 存在: ${onlyZh.join(', ')}`)
if (onlyEn.length) errors.push(`仅 en-US 存在: ${onlyEn.join(', ')}`)

const files = SRC_DIRS.flatMap((d) => walk(d))
const refPattern = /(?:\$?t)\(\s*['"]([A-Za-z][\w$]*\.[\w$.]+)['"]/g
const refs = new Map()
for (const file of files) {
  const text = readFileSync(join(ROOT, file), 'utf8')
  for (const m of text.matchAll(refPattern)) {
    const key = m[1]
    if (!refs.has(key)) refs.set(key, file)
  }
}

const missing = []
for (const [key, file] of refs) {
  const inZh = zh.keys.has(key)
  const inEn = en.keys.has(key)
  if (!inZh || !inEn) missing.push(`${key} (${file}${inZh ? '' : ' · 缺 zh'}${inEn ? '' : ' · 缺 en'})`)
}

console.log('=== i18n 键引用门禁 ===\n')
console.log(`✓ zh-CN 键: ${zh.keys.size}｜en-US 键: ${en.keys.size}`)
console.log(`✓ 代码引用键: ${refs.size}（来自 ${files.length} 个源文件）`)

if (missing.length) {
  console.log(`\n✗ 引用但缺失的键 (${missing.length}):`)
  missing.forEach((m) => console.log('   - ' + m))
  errors.push(`${missing.length} 个被引用的 i18n 键缺失`)
}

if (errors.length) {
  console.log('\n❌ 校验失败:')
  errors.forEach((e) => console.log('   - ' + e))
  process.exit(1)
}

console.log('\n✅ 全部通过 — 代码引用的 i18n 键在 zh/en 均存在，且两侧键集合一致')
