import { describe, it, expect } from 'vitest'
import {
  buildComposition,
  hasProcessEmission,
  hasUnclassifiedGap,
  scopeSubtotal,
} from '../emissionComposition'

/**
 * 这些用例锁定 D0 的核心语义：
 *   total_emission = 三范围 + 过程排放（脱硫 / 线损 / SF6），
 *   且差额必须可被显式拆解，不能在界面上变成两个对不上的数。
 */
describe('scopeSubtotal — 四种历史形态同口径', () => {
  it('数组含 activity_data + emission_factor → 相乘累加', () => {
    expect(scopeSubtotal([
      { activity_data: 100, emission_factor: 2.5 },
      { activity_data: 40, emission_factor: 1.5 },
    ])).toBe(310)
  })

  it('数组含 emission → 直接累加', () => {
    expect(scopeSubtotal([{ emission: 120 }, { emission: 80 }])).toBe(200)
  })

  it('数组含 value → 直接累加', () => {
    expect(scopeSubtotal([{ value: 50 }, { value: 25 }])).toBe(75)
  })

  it('数组裸数字 / 标量 / 数字字符串 → 直接取', () => {
    expect(scopeSubtotal([10, 20, 30])).toBe(60)
    expect(scopeSubtotal(42)).toBe(42)
    expect(scopeSubtotal('88.5')).toBe(88.5)
  })

  it('空值 / 非法值 → 0（不抛异常）', () => {
    expect(scopeSubtotal(null)).toBe(0)
    expect(scopeSubtotal(undefined)).toBe(0)
    expect(scopeSubtotal({})).toBe(0)
    expect(scopeSubtotal('abc')).toBe(0)
    expect(scopeSubtotal(-100)).toBe(0)
  })
})

describe('buildComposition — 三范围 + 过程排放', () => {
  it('只有三范围时，合计等于总量且无过程排放', () => {
    const comp = buildComposition({ scope1: 100, scope2: 200, scope3: 50 })

    expect(comp.sum).toBe(350)
    expect(comp.resolvedTotal).toBe(350)
    expect(comp.gap).toBe(0)
    expect(hasProcessEmission(comp)).toBe(false)
    expect(hasUnclassifiedGap(comp)).toBe(false)
  })

  it('含脱硫时，过程排放计入总量（发电报告）', () => {
    const comp = buildComposition({ scope1: 1000, scope2: 500, desulf: 22 })

    expect(comp.sum).toBe(1522)
    expect(comp.resolvedTotal).toBe(1522)
    expect(comp.gap).toBe(0)
    expect(hasProcessEmission(comp)).toBe(true)

    const desulf = comp.items.find((i) => i.key === 'desulf')
    expect(desulf?.value).toBe(22)
    expect(desulf?.isProcess).toBe(true)
  })

  it('含线损与 SF6 时全部计入（电网报告）', () => {
    const comp = buildComposition({
      scope2: 5810, transmissionLoss: 1510.6, sf6: 717,
    })

    expect(comp.sum).toBe(8037.6)
    expect(comp.resolvedTotal).toBe(8037.6)
    expect(hasProcessEmission(comp)).toBe(true)
  })

  it('显式 total 大于各项之和 → 差额被识别为未归类余量', () => {
    const comp = buildComposition({ scope1: 900, totalEmission: 1200 })

    expect(comp.sum).toBe(900)
    expect(comp.resolvedTotal).toBe(1200)
    expect(comp.gap).toBe(300)
    expect(hasUnclassifiedGap(comp)).toBe(true)
  })

  it('显式 total 小于各项之和 → 不压低（与后端「只准抬高」一致）', () => {
    const comp = buildComposition({ scope1: 900, scope2: 300, totalEmission: 500 })

    expect(comp.sum).toBe(1200)
    expect(comp.resolvedTotal).toBe(1200)
    expect(comp.gap).toBe(0)
  })

  it('null 输入 → 全 0 构成，不抛异常', () => {
    const comp = buildComposition(null)

    expect(comp.sum).toBe(0)
    expect(comp.resolvedTotal).toBe(0)
    expect(comp.items).toHaveLength(6)
    expect(comp.items.every((i) => i.value === 0)).toBe(true)
  })

  it('构成项顺序固定：三范围在前，过程排放在后', () => {
    const comp = buildComposition({ scope1: 1, scope2: 1, scope3: 1, desulf: 1, transmissionLoss: 1, sf6: 1 })

    expect(comp.items.map((i) => i.key)).toEqual([
      'scope1', 'scope2', 'scope3', 'desulf', 'transmissionLoss', 'sf6',
    ])
  })
})

describe('buildComposition — 与后端解析口径一致性（AC-A4）', () => {
  it('数组形态的三范围与后端同口径算出相同合计', () => {
    // 后端 EmissionDataValidator 对同样输入算出 100*2.5 + 40*1.5 = 310
    const comp = buildComposition({
      scope1: [{ activity_data: 100, emission_factor: 2.5 }, { activity_data: 40, emission_factor: 1.5 }],
    })

    expect(comp.sum).toBe(310)
  })

  it('混合形态（数组 + 标量 + 过程排放）合计正确', () => {
    const comp = buildComposition({
      scope1: [{ activity_data: 1000, emission_factor: 1 }],
      scope2: 500,
      desulf: 25,
      sf6: 75,
    })

    expect(comp.sum).toBe(1600)
    expect(hasProcessEmission(comp)).toBe(true)
  })
})