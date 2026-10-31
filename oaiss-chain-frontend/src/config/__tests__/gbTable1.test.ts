import { describe, it, expect } from 'vitest'
import {
  GB_TABLE1,
  deriveCarbonContent,
  resolveOxidationRate,
  lookupFuelDefault,
} from '../gbTable1'

/**
 * 国标表 1 与参数推导。
 *
 * 数据来源：GB/T 32150-2015《中国发电企业温室气体排放核算方法与报告指南（试行）》表 1，
 * 原文见 docs/证据归档/_raw-extract-2026-10-29.txt:1791-1804。
 *
 * 关键事实（逐格核对原文）：
 *   - 燃煤行在原文里只有 2 个单元格，NCV 与 CC 均空、98 落在 CC 列位置 → 语义上属碳氧化率
 *   - 7 行碳氧化率空缺（燃料油/汽油/柴油/炼厂干气/焦炉煤气/其他煤气）
 *   - 焦炉煤气 NCV 是区间值 12726~17981，不是单值
 */
describe('gbTable1 — 国标表 1 数据完整性', () => {
  it('有推荐值的燃料：原油与天然气三项齐全', () => {
    const crude = lookupFuelDefault('crudeOil')
    expect(crude?.ncv).toBe(41816)
    expect(crude?.cc).toBe(20.08)
    expect(crude?.oxidationRate).toBe(0.98)

    const gas = lookupFuelDefault('naturalGas')
    expect(gas?.ncv).toBe(38931)
    expect(gas?.cc).toBe(15.32)
    expect(gas?.oxidationRate).toBe(0.99)
  })

  it('燃煤：NCV 与 CC 无推荐值，碳氧化率 0.98（原文 98 错位到 CC 列，按语义归位）', () => {
    const coal = lookupFuelDefault('rawCoal')
    expect(coal?.ncv).toBeNull()
    expect(coal?.cc).toBeNull()
    expect(coal?.oxidationRate).toBe(0.98)
    expect(coal?.note).toContain('原文错位')
  })

  it('7 项碳氧化率空缺的燃料，oxidationRate 为 null', () => {
    for (const key of ['fuelOil', 'gasoline', 'diesel', 'refineryGas', 'cokeOvenGas', 'otherGas']) {
      expect(lookupFuelDefault(key)?.oxidationRate, key).toBeNull()
    }
  })

  it('焦炉煤气 NCV 是区间值，不能当单值用', () => {
    const coke = lookupFuelDefault('cokeOvenGas')
    expect(coke?.ncv).toBeNull()
    expect(coke?.ncvRange).toEqual([12726, 17981])
  })

  it('每个条目都带适用标准引用（可溯源）', () => {
    for (const [key, item] of Object.entries(GB_TABLE1)) {
      expect(item.standardRef, key).toBeTruthy()
    }
  })
})

describe('deriveCarbonContent — CC = C% × 10⁶ / NCV', () => {
  it('国标式验算：58.2% 与 20908 kJ/kg → 27.84 tC/TJ', () => {
    expect(deriveCarbonContent(58.2, 20908)).toBeCloseTo(27.84, 2)
  })

  it('45% 与 18000 → 25.00，落在煤的常见区间', () => {
    expect(deriveCarbonContent(45, 18000)).toBeCloseTo(25.0, 2)
  })

  it('65.5% 与 23500 → 27.87', () => {
    expect(deriveCarbonContent(65.5, 23500)).toBeCloseTo(27.87, 2)
  })

  it('典型煤样的推导结果均在 20~35 tC/TJ 合理区间', () => {
    const samples = [
      [40, 16000], [45, 18000], [50, 20000], [55, 21000],
      [58.2, 20908], [60, 22000], [65.5, 23500],
    ]
    for (const [c, ncv] of samples) {
      const cc = deriveCarbonContent(c, ncv)
      expect(cc, `C=${c}% NCV=${ncv}`).toBeGreaterThan(20)
      expect(cc, `C=${c}% NCV=${ncv}`).toBeLessThan(35)
    }
  })

  it('输入非法时返回 null（不抛异常、不编造值）', () => {
    expect(deriveCarbonContent(null, 20908)).toBeNull()
    expect(deriveCarbonContent(58.2, null)).toBeNull()
    expect(deriveCarbonContent(0, 20908)).toBeNull()
    expect(deriveCarbonContent(58.2, 0)).toBeNull()
    expect(deriveCarbonContent(-5, 20908)).toBeNull()
    expect(deriveCarbonContent(58.2, -1)).toBeNull()
  })
})

describe('resolveOxidationRate — 灰渣反推优先于表 1 默认', () => {
  it('有灰渣反推值时优先采用', () => {
    const r = resolveOxidationRate('rawCoal', 0.9905)
    expect(r.value).toBeCloseTo(0.9905, 4)
    expect(r.source).toBe('measured')
  })

  it('无灰渣数据时回落表 1 默认值', () => {
    const r = resolveOxidationRate('rawCoal', null)
    expect(r.value).toBeCloseTo(0.98, 4)
    expect(r.source).toBe('table1')
  })

  it('表 1 也无默认值时返回 null，要求用户填写', () => {
    const r = resolveOxidationRate('fuelOil', null)
    expect(r.value).toBeNull()
    expect(r.source).toBe('required')
  })

  it('反推值越界（>1 或 <0）被拒绝，回落默认', () => {
    expect(resolveOxidationRate('rawCoal', 1.5).source).toBe('table1')
    expect(resolveOxidationRate('rawCoal', -0.2).source).toBe('table1')
  })
})
