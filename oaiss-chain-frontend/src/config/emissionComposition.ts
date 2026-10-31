/**
 * 排放总量构成拆解。
 *
 * ## 为什么需要这个模块
 *
 * 系统里 `total_emission` 与 `scope1+scope2+scope3` 并不总是相等：
 * 解析器会把**过程排放**（脱硫 `desulf`、电网线损 `transmissionLoss`、SF6 泄漏 `sf6`）
 * 也加进总量，但数据库只有三个 scope 列（`EmissionDataValidator:87-101` 与
 * `V1__init_schema.sql:157-161`）。
 *
 * 后果是审核端出现「上方显示总量 1200，下方明细合计 900」两个对不上的数字，
 * 且没有任何解释。
 *
 * 本模块把「总量怎么构成的」算清楚，供填报页、详情页、审核端三处共用，
 * 让用户能自己逐项核对（D0 = 含过程排放 + 差额显式拆解）。
 */
import type { EmissionRowShape } from './emissionShape'

/** 构成项：每个键对应一种排放来源 */
export type CompositionItemKey =
  | 'scope1'
  | 'scope2'
  | 'scope3'
  | 'desulf'
  | 'transmissionLoss'
  | 'sf6'

export interface CompositionItem {
  key: CompositionItemKey
  /** 数值（吨 CO2e） */
  value: number
  /** 是否属于「过程排放」（脱硫/线损/SF6），用于界面分组 */
  isProcess: boolean
}

export interface EmissionComposition {
  /** 各项明细，固定顺序：三范围在前，过程排放在后 */
  items: CompositionItem[]
  /** 各项之和 */
  sum: number
  /** 表单里显式给出的 totalEmission（可能为 null） */
  explicitTotal: number | null
  /** 最终应采用的总量（与后端解析口径一致） */
  resolvedTotal: number
  /** resolvedTotal 与 sum 的差额；非 0 表示存在未归类的余量 */
  gap: number
}

const ITEM_ORDER: Array<{ key: CompositionItemKey; isProcess: boolean }> = [
  { key: 'scope1', isProcess: false },
  { key: 'scope2', isProcess: false },
  { key: 'scope3', isProcess: false },
  { key: 'desulf', isProcess: true },
  { key: 'transmissionLoss', isProcess: true },
  { key: 'sf6', isProcess: true },
]

const toNumber = (v: unknown): number => {
  const n = Number(v)
  return Number.isFinite(n) && n >= 0 ? n : 0
}

/**
 * 计算单个 scope 的小计，与后端 `EmissionDataValidator.calculateScopeTotal` 同口径。
 *
 * 支持四种历史形态：
 *   1. 数组元素含 `activity_data` + `emission_factor` → 相乘累加
 *   2. 数组元素含 `emission` → 直接累加
 *   3. 数组元素含 `value` → 直接累加
 *   4. 数组元素是裸数字 / scope 本身是数字或数字字符串 → 直接取
 */
export function scopeSubtotal(raw: unknown): number {
  if (raw === null || raw === undefined) return 0

  if (Array.isArray(raw)) {
    return raw.reduce<number>((acc, item) => {
      if (typeof item === 'number') return acc + toNumber(item)
      if (typeof item === 'string') return acc + toNumber(item)
      if (item && typeof item === 'object') {
        const row = item as EmissionRowShape
        if (row.activity_data !== undefined && row.emission_factor !== undefined) {
          return acc + toNumber(row.activity_data) * toNumber(row.emission_factor)
        }
        if (row.emission !== undefined) return acc + toNumber(row.emission)
        if (row.value !== undefined) return acc + toNumber(row.value)
      }
      return acc
    }, 0)
  }

  if (typeof raw === 'number' || typeof raw === 'string') {
    return toNumber(raw)
  }

  return 0
}

/**
 * 把 `emissionData` 拆成「三范围 + 过程排放」的构成清单。
 *
 * @param parsed 已 JSON.parse 的对象；传 null 时返回全 0 构成
 */
export function buildComposition(parsed: Record<string, unknown> | null): EmissionComposition {
  const items: CompositionItem[] = ITEM_ORDER.map(({ key, isProcess }) => ({
    key,
    isProcess,
    value: scopeSubtotal(parsed?.[key]),
  }))

  const sum = items.reduce((acc, item) => acc + item.value, 0)

  const explicitRaw = parsed?.['totalEmission']
  const explicitTotal = explicitRaw === undefined || explicitRaw === null
    ? null
    : toNumber(explicitRaw)

  // 与后端一致的解析口径：显式 total 只准「抬高」，不准压低
  let resolvedTotal = sum
  if (explicitTotal !== null && explicitTotal > 0 && explicitTotal > sum) {
    resolvedTotal = explicitTotal
  }

  return {
    items,
    sum: Number(sum.toFixed(4)),
    explicitTotal,
    resolvedTotal: Number(resolvedTotal.toFixed(4)),
    gap: Number((resolvedTotal - sum).toFixed(4)),
  }
}

/** 构成里是否存在任何过程排放（用于决定是否显示过程排放分组） */
export function hasProcessEmission(comp: EmissionComposition): boolean {
  return comp.items.some((item) => item.isProcess && item.value > 0)
}

/** 是否存在未归类差额（显式 total 大于各项之和） */
export function hasUnclassifiedGap(comp: EmissionComposition): boolean {
  return Math.abs(comp.gap) > 0.0001
}