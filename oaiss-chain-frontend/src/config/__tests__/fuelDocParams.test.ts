import { describe, it, expect } from 'vitest'
import {
  COAL_CC_TYPICAL_RANGE,
  createEmptyFuelParams,
  fuelTableKey,
  hasDocInputs,
  isCoalCcReasonable,
  resolveFuelParams,
  type FuelDocParams,
} from '../fuelDocParams'

/**
 * 这些用例锁定两次真实事故：
 *   1. 洗精煤/其他洗煤被映射到 `fuelOil`、其他煤被映射到 `otherGas`，
 *      导致煤被套上油/气参数，FC=10000 时算出 31704 tCO₂e（正确约 20913，偏差 50%）。
 *   2. 这三种煤的碳氧化率变成 `null`，后端直接拒算——企业把三个单据值全填了也算不出来。
 */
describe('fuelTableKey — 5 个煤炭小节全部映射到燃煤', () => {
  it('全部 5 个小节映射到 rawCoal', () => {
    for (const key of ['rawCoal', 'cleanedCoal', 'otherWashedCoal', 'briquette', 'otherCoal']) {
      expect(fuelTableKey(key), key).toBe('rawCoal')
    }
  })

  it('绝不可映射到 fuelOil / otherGas（那是不同燃料）', () => {
    for (const key of ['rawCoal', 'cleanedCoal', 'otherWashedCoal', 'briquette', 'otherCoal']) {
      expect(fuelTableKey(key)).not.toBe('fuelOil')
      expect(fuelTableKey(key)).not.toBe('otherGas')
    }
  })

  it('未知 key 回落 rawCoal，不抛异常', () => {
    expect(fuelTableKey('unknownFuel')).toBe('rawCoal')
    expect(fuelTableKey('')).toBe('rawCoal')
  })
})

describe('resolveFuelParams — 碳氧化率兜底（历史事故 2）', () => {
  it('只填煤耗时，5 个煤小节都能拿到表 1 的 0.98', () => {
    for (const key of ['rawCoal', 'cleanedCoal', 'otherWashedCoal', 'briquette', 'otherCoal']) {
      const p = { ...createEmptyFuelParams(), fc: 10000 }
      const r = resolveFuelParams(fuelTableKey(key), p)
      expect(r.of, key).toBeCloseTo(0.98, 4)
      expect(r.sources.of, key).toBe('table1')
    }
  })

  it('灰渣反推值优先于表 1 默认', () => {
    const p = { ...createEmptyFuelParams(), fc: 10000, ashOf: 0.9905 }
    const r = resolveFuelParams('rawCoal', p)
    expect(r.of).toBeCloseTo(0.9905, 4)
    expect(r.sources.of).toBe('measured')
  })

  it('手工覆盖优先于灰渣反推', () => {
    const p = { ...createEmptyFuelParams(), fc: 10000, ashOf: 0.9905, ofManual: 0.95 }
    const r = resolveFuelParams('rawCoal', p)
    expect(r.of).toBeCloseTo(0.95, 4)
    expect(r.sources.of).toBe('manual')
  })
})

describe('resolveFuelParams — CC 推导', () => {
  it('填化验单碳含量 + 发热量 → 按国标式推导 CC', () => {
    const p: FuelDocParams = {
      ...createEmptyFuelParams(),
      fc: 10000,
      carbonPercent: 58.2,
      ncvMeasured: 20908,
    }
    const r = resolveFuelParams('rawCoal', p)
    expect(r.cc).toBeCloseTo(27.8362, 2)
    expect(r.sources.cc).toBe('derived')
    expect(r.derivedCc).toBeCloseTo(27.8362, 2)
  })

  it('只有碳含量没有发热量 → 不推导（国标式要求实测 NCV）', () => {
    const p = { ...createEmptyFuelParams(), fc: 10000, carbonPercent: 58.2 }
    const r = resolveFuelParams('rawCoal', p)
    expect(r.cc).toBeNull()
    expect(r.derivedCc).toBeNull()
    expect(r.sources.cc).toBe('required')
  })

  it('燃煤无 CC 推荐值，缺实测即为 null（不编造）', () => {
    const p = { ...createEmptyFuelParams(), fc: 10000 }
    const r = resolveFuelParams('rawCoal', p)
    expect(r.cc).toBeNull()
    expect(r.sources.cc).toBe('required')
  })

  it('手工指定 CC 优先于推导值', () => {
    const p = {
      ...createEmptyFuelParams(),
      fc: 10000, carbonPercent: 58.2, ncvMeasured: 20908, ccManual: 30,
    }
    const r = resolveFuelParams('rawCoal', p)
    expect(r.cc).toBe(30)
    expect(r.sources.cc).toBe('manual')
  })

  it('有表 1 推荐值的燃料（原油），无实测时落推荐值', () => {
    const r = resolveFuelParams('crudeOil', { ...createEmptyFuelParams(), fc: 100 })
    expect(r.ncv).toBe(41816)
    expect(r.cc).toBe(20.08)
    expect(r.sources.ncv).toBe('table1')
    expect(r.sources.cc).toBe('table1')
  })
})

describe('resolveFuelParams — 完整单据路径（AC-3）', () => {
  it('企业只填三个单据值，就能得到完整的 ncv/cc/of', () => {
    const p: FuelDocParams = {
      ...createEmptyFuelParams(),
      fc: 10000,
      carbonPercent: 58.2,
      ncvMeasured: 20908,
    }
    const r = resolveFuelParams('rawCoal', p)

    expect(r.ncv).toBe(20908)          // 化验单发热量
    expect(r.cc).toBeCloseTo(27.8362, 2) // 系统推导
    expect(r.of).toBeCloseTo(0.98, 4)    // 表 1 兜底

    // 三项都不为 null → 后端不会拒算
    expect(r.ncv).not.toBeNull()
    expect(r.cc).not.toBeNull()
    expect(r.of).not.toBeNull()
  })
})

describe('isCoalCcReasonable — 合理性校验', () => {
  it('27.84 落在煤的常见区间内', () => {
    expect(isCoalCcReasonable('rawCoal', 27.84)).toBe(true)
    expect(COAL_CC_TYPICAL_RANGE).toEqual([20, 35])
  })

  it('3.5 严重偏低 → 判定异常（多半是把 58.2% 填成 0.582）', () => {
    expect(isCoalCcReasonable('rawCoal', 3.5)).toBe(false)
  })

  it('null 不判定为异常', () => {
    expect(isCoalCcReasonable('rawCoal', null)).toBe(true)
  })

  it('非煤燃料不做区间判定', () => {
    expect(isCoalCcReasonable('crudeOil', 3.5)).toBe(true)
  })

  it('briquette 也按煤判定（历史漏判点）', () => {
    expect(isCoalCcReasonable('briquette', 3.5)).toBe(false)
  })
})

describe('hasDocInputs — 表单完整性', () => {
  it('全空返回 false', () => {
    expect(hasDocInputs(createEmptyFuelParams())).toBe(false)
  })

  it('任一单据字段有值即返回 true', () => {
    expect(hasDocInputs({ ...createEmptyFuelParams(), fc: 100 })).toBe(true)
    expect(hasDocInputs({ ...createEmptyFuelParams(), carbonPercent: 58.2 })).toBe(true)
    expect(hasDocInputs({ ...createEmptyFuelParams(), ncvMeasured: 20908 })).toBe(true)
  })
})
