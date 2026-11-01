/**
 * 菜单 tab 映射完整性测试。
 *
 * 背景：顶栏一级页签的文案由 TAB_LABEL 把 tab → i18n key 映射得到。
 * 若某个 tab 值没有对应映射，界面会直接显示原始 tab 名（如 "chain"），
 * 而且**不会报错**——用户看到的是一个英文小写单词，以为是样式问题。
 *
 * 这个缺陷真实发生过：管理员新增「链上管理」菜单时用了 tab: 'chain'，
 * 但 TAB_LABEL 里没有 chain，导航栏渲染出了字面量 "chain"。
 * 单元测试与类型检查都发现不了（tab 是普通 string），
 * 只有 E2E 截图才看得出。
 */
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()

function readSource(rel: string): string {
  return readFileSync(join(root, rel), 'utf8')
}

/** 从 menu.ts 提取所有用到的 tab 值 */
function collectMenuTabs(): Set<string> {
  const src = readSource('src/config/menu.ts')
  return new Set([...src.matchAll(/tab:\s*'([^']+)'/g)].map(m => m[1]))
}

/** 从 layout/index.vue 提取 TAB_LABEL 登记的 tab */
function collectTabLabelKeys(): Set<string> {
  const src = readSource('src/layout/index.vue')
  const block = src.slice(src.indexOf('const TAB_LABEL'))
  const body = block.slice(0, block.indexOf('\n}'))
  return new Set([...body.matchAll(/^\s{2}(\w+):\s*'layout\./gm)].map(m => m[1]))
}

/** zh-CN locale 中实际存在的 layout.tab* 键 */
function collectLocaleTabKeys(): Set<string> {
  const src = readSource('src/i18n/locales/zh-CN.ts')
  return new Set([...src.matchAll(/^\s{4}(tab\w+):/gm)].map(m => m[1]))
}

describe('菜单 tab 映射完整性', () => {
  it('menu.ts 用到的每个 tab 都在 TAB_LABEL 中有映射', () => {
    const menuTabs = collectMenuTabs()
    const labelKeys = collectTabLabelKeys()
    const missing = [...menuTabs].filter(t => !labelKeys.has(t))

    expect(
      missing,
      `以下 tab 值缺少 TAB_LABEL 映射，界面会直接显示原始 tab 名：${missing.join(', ')}`,
    ).toEqual([])
  })

  it('TAB_LABEL 引用的每个 layout.tab* key 在 zh-CN 中真实存在', () => {
    const localeKeys = collectLocaleTabKeys()
    const src = readSource('src/layout/index.vue')
    const block = src.slice(src.indexOf('const TAB_LABEL'))
    const body = block.slice(0, block.indexOf('\n}'))
    const referenced = [...body.matchAll(/'layout\.(tab\w+)'/g)].map(m => m[1])
    const missing = [...new Set(referenced)].filter(k => !localeKeys.has(k))

    expect(
      missing,
      `TAB_LABEL 引用了不存在的 i18n key（界面会显示键名原文）：${missing.join(', ')}`,
    ).toEqual([])
  })
})