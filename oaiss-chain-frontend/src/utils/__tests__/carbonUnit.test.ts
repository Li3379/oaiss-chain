import { describe, it, expect } from 'vitest'
import { toStandardCarbonContent, toStandardNcv } from '../carbonUnit'

/**
 * 回归背景：模板值（GJ/t、tC/GJ）被原样送进要 kJ/kg、tC/TJ 的反推接口，
 * 会让碳氧化率被钳制成 0。以下用例锁定「量级归一」这条防线。
 */
describe('carbonUnit — 低位发热量归一到 kJ/kg', () => {
  it('GJ/t 量级（<100）乘以 1000', () => {
    expect(toStandardNcv(20.908, 20000)).toBeCloseTo(20908, 6)
    expect(toStandardNcv(35, 20000)).toBe(35000)
    expect(toStandardNcv(99.9, 20000)).toBeCloseTo(99900, 6)
  })

  it('已是 kJ/kg 量级（>=100）原样返回', () => {
    expect(toStandardNcv(20908, 20000)).toBe(20908)
    expect(toStandardNcv(100, 20000)).toBe(100)
    expect(toStandardNcv(38931, 20000)).toBe(38931)
  })

  it('空值 / 0 / NaN 回退到默认值', () => {
    expect(toStandardNcv(null, 20000)).toBe(20000)
    expect(toStandardNcv(undefined, 20000)).toBe(20000)
    expect(toStandardNcv(0, 20000)).toBe(20000)
    expect(toStandardNcv(Number.NaN, 20000)).toBe(20000)
  })

  it('负数按绝对值判量级（防御异常输入）', () => {
    expect(toStandardNcv(-20.908, 20000)).toBeCloseTo(-20908, 6)
  })
})

describe('carbonUnit — 含碳量归一到 tC/TJ', () => {
  it('tC/GJ 量级（<1）乘以 1000', () => {
    expect(toStandardCarbonContent(0.0263, 26.3)).toBeCloseTo(26.3, 6)
    expect(toStandardCarbonContent(0.0202, 26.3)).toBeCloseTo(20.2, 6)
    expect(toStandardCarbonContent(0.9, 26.3)).toBeCloseTo(900, 6)
  })

  it('已是 tC/TJ 量级（>=1）原样返回', () => {
    expect(toStandardCarbonContent(26.3, 26.3)).toBe(26.3)
    expect(toStandardCarbonContent(1, 26.3)).toBe(1)
    expect(toStandardCarbonContent(15.32, 26.3)).toBe(15.32)
  })

  it('空值 / 0 / NaN 回退到默认值', () => {
    expect(toStandardCarbonContent(null, 26.3)).toBe(26.3)
    expect(toStandardCarbonContent(undefined, 26.3)).toBe(26.3)
    expect(toStandardCarbonContent(0, 26.3)).toBe(26.3)
    expect(toStandardCarbonContent(Number.NaN, 26.3)).toBe(26.3)
  })
})

describe('carbonUnit — 端到端：归一后不应产生 OF=0', () => {
  /**
   * 复现原始故障的算术：灰渣含碳量与产渣比固定，
   * 用模板值（未归一）会让分母小 1e6，OF 被钳成 0；
   * 归一后应得接近 0.99 的合理值。
   */
  const slag = 800 * 0.02
  const ash = 1200 * (0.03 / 0.99)
  const unoxidized = (slag + ash) * 1e6
  const fc = 10000

  it('未归一的模板值会算出负 OF（会被钳成 0）', () => {
    const denom = fc * 20.908 * 0.0263
    const of = 1 - unoxidized / denom
    expect(of).toBeLessThan(0)
  })

  it('归一后的值得到约 0.99 的合理 OF', () => {
    const denom = fc * toStandardNcv(20.908, 20000) * toStandardCarbonContent(0.0263, 26.3)
    const of = 1 - unoxidized / denom
    expect(of).toBeGreaterThan(0.98)
    expect(of).toBeLessThanOrEqual(1)
  })
})