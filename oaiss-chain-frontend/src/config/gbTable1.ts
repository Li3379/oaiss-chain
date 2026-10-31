/**
 * GB/T 32150-2015 表 1「常用化石燃料相关参数缺省值」+ 中间参数推导。
 *
 * ## 为什么要这个文件
 *
 * 原表单要求企业直接填 `NCV`、`CC`、`OF` 三个专业参数，但：
 *   - 煤质化验单上印的是「元素碳含量 Cad = 58.2%」，不是 `CC = 26.3 tC/TJ`；
 *   - 多数企业的能源管理员不知道这三者怎么换算。
 *
 * 国标原文（`docs/证据归档/_raw-extract-2026-10-29.txt:1812`）给了推导式：
 *
 *     CC_煤 = C_煤 × 10⁶ / NCV_煤
 *
 * 因此企业只需填**化验单上真实印着的两个数**（碳含量 %、发热量 kJ/kg），
 * 系统按国标式算出 CC。**公式、参数、口径一个都没改**——只是把输入层换了抽象。
 *
 * ## 数据完整性说明（逐格核对原文 1791–1804 行）
 *
 * 1. **燃煤行原文只有 2 个单元格**：NCV 与 CC 均空，`98` 落在 CC 列的位置。
 *    按语义（原油同为 98、煤的碳氧化率典型值 98%）归位为碳氧化率。
 * 2. **7 项碳氧化率空缺**：燃料油、汽油、柴油、炼厂干气、焦炉煤气、其他煤气。
 * 3. **焦炉煤气 NCV 是区间** `12726~17981`，不是单值，不能直接代入乘法公式。
 *
 * 凡本表无推荐值的项，一律返回 `null` 并标注原因，**不编造数字**。
 */
import {
  toStandardCarbonContent,
  toStandardNcv,
} from '../utils/carbonUnit'
import type { RuleSource } from './carbonSourceHints'

/** 表 1 中的一种燃料条目 */
export interface FuelDefault {
  /** 中文名 */
  name: string
  /** 平均低位发热值 (kJ/kg)；无推荐值为 null */
  ncv: number | null
  /** 单位热值含碳量 (tC/TJ)；无推荐值为 null */
  cc: number | null
  /** 碳氧化率 (0~1)；无推荐值为 null */
  oxidationRate: number | null
  /** NCV 为区间值时的上下界 (kJ/kg)，用于界面提示 */
  ncvRange?: [number, number]
  /** 适用标准/出处引用，供「因子可溯源」使用 */
  standardRef: string
  /** 缺值说明（为什么是 null） */
  note?: string
}

/** GB/T 32150-2015 表 1 逐行数据 */
export const GB_TABLE1: Record<string, FuelDefault> = {
  rawCoal: {
    name: '燃煤',
    ncv: null,
    cc: null,
    oxidationRate: 0.98,
    standardRef: 'GB/T 32150-2015 表 1',
    note: '原文该行 NCV 与 CC 两格为空、98 落在 CC 列（原文错位），按语义归位为碳氧化率；发热量与含碳量国标无推荐值，必须实测或采用供应商化验单',
  },
  crudeOil: {
    name: '原油',
    ncv: 41816,
    cc: 20.08,
    oxidationRate: 0.98,
    standardRef: 'GB/T 32150-2015 表 1',
  },
  fuelOil: {
    name: '燃料油',
    ncv: 41816,
    cc: 21.1,
    oxidationRate: null,
    standardRef: 'GB/T 32150-2015 表 1',
    note: '原文该行碳氧化率空缺，需实测或按企业工艺确定',
  },
  gasoline: {
    name: '汽油',
    ncv: 43070,
    cc: 18.9,
    oxidationRate: null,
    standardRef: 'GB/T 32150-2015 表 1',
    note: '原文该行碳氧化率空缺',
  },
  diesel: {
    name: '柴油',
    ncv: 42652,
    cc: 20.2,
    oxidationRate: null,
    standardRef: 'GB/T 32150-2015 表 1',
    note: '原文该行碳氧化率空缺',
  },
  refineryGas: {
    name: '炼厂干气',
    ncv: 45998,
    cc: 18.2,
    oxidationRate: null,
    standardRef: 'GB/T 32150-2015 表 1',
    note: '原文该行碳氧化率空缺',
  },
  naturalGas: {
    name: '天然气',
    ncv: 38931,
    cc: 15.32,
    oxidationRate: 0.99,
    standardRef: 'GB/T 32150-2015 表 1',
  },
  cokeOvenGas: {
    name: '焦炉煤气',
    ncv: null,
    cc: 13.58,
    oxidationRate: null,
    ncvRange: [12726, 17981],
    standardRef: 'GB/T 32150-2015 表 1',
    note: '原文该行发热值为区间 12726~17981，需按实际气源选定单值；碳氧化率空缺',
  },
  otherGas: {
    name: '其他煤气',
    ncv: 52270,
    cc: 12.2,
    oxidationRate: null,
    standardRef: 'GB/T 32150-2015 表 1',
    note: '原文该行碳氧化率空缺',
  },
}

/** 煤的合理 CC 区间 (tC/TJ)，用于合理性提示（非国标强制值） */
export const COAL_CC_TYPICAL_RANGE: [number, number] = [20, 35]

/** 查询某种燃料的表 1 条目；未知 key 返回 null */
export function lookupFuelDefault(key: string): FuelDefault | null {
  return GB_TABLE1[key] ?? null
}

/**
 * 由元素碳含量与低位发热量推导单位热值含碳量。
 *
 * 国标式（GB/T 32150-2015，原文 1812 行）：
 *     CC (tC/TJ) = C (%) × 10⁶ / NCV (kJ/kg)
 *
 * 量纲核对：`C/100 × 1e6 / NCV` = `C × 1e4 / NCV`，
 * 对 `C=58.2, NCV=20908` 得 `27.84 tC/TJ`，与行业典型值一致。
 *
 * @param carbonPercent 元素碳含量，百分数（58.2 表示 58.2%）
 * @param ncvKjPerKg    低位发热量 (kJ/kg)
 * @returns tC/TJ；任一输入非法时返回 null（不编造）
 */
export function deriveCarbonContent(
  carbonPercent: number | null | undefined,
  ncvKjPerKg: number | null | undefined,
): number | null {
  if (
    carbonPercent === null || carbonPercent === undefined ||
    ncvKjPerKg === null || ncvKjPerKg === undefined
  ) {
    return null
  }
  if (!Number.isFinite(carbonPercent) || !Number.isFinite(ncvKjPerKg)) return null
  if (carbonPercent <= 0 || ncvKjPerKg <= 0) return null

  const cc = (carbonPercent * 1e6) / ncvKjPerKg / 100
  return Number(cc.toFixed(4))
}

/** 碳氧化率的取用来源 */
export type OxidationRateSource = 'measured' | 'table1' | 'required'

export interface OxidationRateResult {
  value: number | null
  source: OxidationRateSource
  /** 供界面展示的说明 */
  note?: string
}

/**
 * 确定碳氧化率的取用值，优先级：灰渣实测反推 > 表 1 推荐值 > 要求用户填写。
 *
 * @param fuelKey          表 1 中的燃料键
 * @param measuredFromAsh  灰渣反推得到的值（0~1），无则为 null
 */
export function resolveOxidationRate(
  fuelKey: string,
  measuredFromAsh: number | null | undefined,
): OxidationRateResult {
  if (
    measuredFromAsh !== null && measuredFromAsh !== undefined &&
    Number.isFinite(measuredFromAsh) &&
    measuredFromAsh > 0 && measuredFromAsh <= 1
  ) {
    return {
      value: measuredFromAsh,
      source: 'measured',
      note: '由灰渣含碳量实测反推',
    }
  }

  const table1 = lookupFuelDefault(fuelKey)
  if (table1?.oxidationRate !== null && table1?.oxidationRate !== undefined) {
    return {
      value: table1.oxidationRate,
      source: 'table1',
      note: `采用国标表 1 推荐值 ${(table1.oxidationRate * 100).toFixed(0)}%`,
    }
  }

  return {
    value: null,
    source: 'required',
    note: '国标表 1 对燃料无碳氧化率推荐值，请查阅企业工艺数据或实测',
  }
}

/**
 * 取某燃料可直接使用的推荐值（已归一到计算口径）。
 * 供表单「一键填入国标推荐值」使用；无推荐值的项返回 null。
 */
export function recommendedValues(fuelKey: string): {
  ncvKjPerKg: number | null
  ccTcPerTj: number | null
  oxidationRate: number | null
  source: RuleSource
} {
  const item = lookupFuelDefault(fuelKey)
  return {
    // 表 1 的 NCV 本身就是 kJ/kg、CC 本身就是 tC/TJ，无需换算；
    // 这里仍走一次归一是为了防御未来数据源混用口径。
    ncvKjPerKg: item?.ncv !== null && item?.ncv !== undefined
      ? toStandardNcv(item.ncv, item.ncv)
      : null,
    ccTcPerTj: item?.cc !== null && item?.cc !== undefined
      ? toStandardCarbonContent(item.cc, item.cc)
      : null,
    oxidationRate: item?.oxidationRate ?? null,
    source: { type: 'gbTable1', ref: item?.standardRef ?? 'GB/T 32150-2015 表 1' },
  }
}
