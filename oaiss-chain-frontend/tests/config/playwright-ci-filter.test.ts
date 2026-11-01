/**
 * Playwright 配置的 CI 过滤语义测试。
 *
 * 背景：smoke 目录里混着三类 spec，用途完全不同——如果配置改错，
 * 要么把长跑用例放进自动回归（job 超时被 cancelled），要么把正式回归
 * 也一起排掉（CI 假装通过，实际什么都没测）。两种都不报错，只是后果不同。
 *
 * 因此这里把「CI 到底该跑哪些」钉成断言。
 */
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const configPath = join(process.cwd(), 'playwright.config.ts')
const source = readFileSync(configPath, 'utf8')

/**
 * 提取 testIgnore 中登记的排除模式。
 *
 * 实现已从「三元 + 字面量数组」改为「按开关累加」，因此这里直接扫描
 * 整个 testIgnore 表达式块里的字符串字面量。断言只关心「哪些模式被排除」，
 * 不关心它用什么写法拼出来。
 */
function extractTestIgnore(): string[] {
  const start = source.indexOf('testIgnore:')
  if (start < 0) return []
  const block = source.slice(start, start + 900)
  return [...block.matchAll(/'(\*\*\/[^']+)'/g)].map(x => x[1])
}

describe('playwright.config CI 过滤', () => {
  it('CI 中排除人工探针与调试脚本', () => {
    const patterns = extractTestIgnore()
    expect(patterns).toContain('**/*-probe.spec.ts')
    expect(patterns).toContain('**/*debug*.spec.ts')
  })

  it('CI 中排除 full-functional 全矩阵用例', () => {
    // 该 spec 在单条用例里串行跑 83 个子场景，自声明 45 分钟超时，
    // 实测 17.6 分钟——叠上前面的构建步骤必然撞穿 30 分钟 job 上限。
    // 它是全量验收脚本，不是回归测试，应由 e2e-full-matrix 工作流手动触发。
    expect(extractTestIgnore()).toContain('**/full-functional.spec.ts')
  })

  it('CI 中排除 d9/d10 纯前端 mock 型 spec', () => {
    // 它们在 CI 上稳定失败（mock 未生效、页面读真实空后端），
    // 本地与手动全矩阵已覆盖；不放行则 CI 常驻 17 项噪音。
    const patterns = extractTestIgnore()
    expect(patterns).toContain('**/d9-*.spec.js')
    expect(patterns).toContain('**/d10-*.spec.js')
  })

  it('CI 仍保留各角色的 *.smoke.spec.ts 作为回归', () => {
    const patterns = extractTestIgnore()
    // 不能被上面的排除误伤：smoke 套件是 CI 唯一真正的回归保障
    expect(patterns.some(p => p.includes('smoke.spec'))).toBe(false)
  })
})
