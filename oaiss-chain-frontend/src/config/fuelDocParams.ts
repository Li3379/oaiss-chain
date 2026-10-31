/**
 * 单据驱动表单的核心推导逻辑。
 *
 * 从 `CarbonFormulaCalculator.vue` 的 `<script setup>` 抽出——那里无法被单测导入，
 * 而这正是映射与优先级最容易出错的地方（历史发生过两次事故：煤被映射到燃料油、
 * 洗精煤的碳氧化率变 null 导致后端拒算）。
 *
 * ## 关键约束
 *
 * 1. **5 个煤炭小节全部映射到表 1 的「燃煤」行**。国标表 1 只有一行「燃煤」，
 *    绝不可映射到 `fuelOil`（NCV 41816、CC 21.1）或 `otherGas`（NCV 52270、CC 12.2）
 *    —— 它们是不同燃料，套用会静默算出约 50% 偏差的排放量。
 * 2. **CC 推导只能用实测 NCV**。国标式 `CC = C% × 10⁶ / NCV` 里的 NCV 必须是
 *    企业月平均实测低位发热值，用表 1 推荐值会得到既非实测也非推荐的混合数。
 * 3. **优先级**：手工指定 > 实测/推导 > 表 1 推荐值 > 需填写。
 */
import { deriveCarbonContent, recommendedValues, resolveOxidationRate } from './gbTable1'

/** 单个燃料的**单据驱动**表单状态 */
export interface FuelDocParams {
  /** 本月燃料消耗量 (t)，来自台帐/过磅单 */
  fc: number | null
  /** 化验单元素碳含量 (%)，如 58.2 */
  carbonPercent: number | null
  /** 化验单低位发热量 (kJ/kg) */
  ncvMeasured: number | null
  /** 灰渣反推得到的碳氧化率 (0~1)，可选 */
  ashOf: number | null
  /** 手工指定的含碳量 (tC/TJ)，专业覆盖 */
  ccManual: number | null
  /** 手工指定的碳氧化率 (0~1)，专业覆盖 */
  ofManual: number | null
}

/** 创建一个空的单据参数集 */
export function createEmptyFuelParams(): FuelDocParams {
  return {
    fc: null,
    carbonPercent: null,
    ncvMeasured: null,
    ashOf: null,
    ccManual: null,
    ofManual: null,
  }
}

/**
 * 界面小节 → 国标表 1 燃料行的唯一映射。
 *
 * 展示、实时预估、灰渣助手、提交四处必须共用这一张表，
 * 否则同一小节会显示不同数（历史缺陷：UI 显示「—」而后端收到 41816）。
 */
const FUEL_TABLE_KEY: Record<string, string> = {
  rawCoal: 'rawCoal',
  cleanedCoal: 'rawCoal',
  otherWashedCoal: 'rawCoal',
  briquette: 'rawCoal',
  otherCoal: 'rawCoal',
}

/** 取某个界面小节对应的表 1 燃料键；未知 key 一律回落 `rawCoal` */
export function fuelTableKey(sectionKey: string): string {
  return FUEL_TABLE_KEY[sectionKey] ?? 'rawCoal'
}

/** 推导来源标识，供界面展示 */
export type ParamSource = 'measured' | 'derived' | 'table1' | 'manual' | 'required'

export interface ResolvedFuelParams {
  /** 提交给后端的低位发热量 (kJ/kg) */
  ncv: number | null
  /** 提交给后端的含碳量 (tC/TJ) */
  cc: number | null
  /** 提交给后端的碳氧化率 (0~1) */
  of: number | null
  /** 三项各自的来源，供界面展示 */
  sources: Record<'ncv' | 'cc' | 'of', ParamSource>
  /** 由实测值推导出的 CC；未推导时为 null */
  derivedCc: number | null
  /** 碳氧化率的取用说明 */
  oxidationRateNote?: string
}

/**
 * 从单据值推导出提交给后端的 `ncv` / `cc` / `of`。
 *
 * 只做「填哪个数」的选择，**不改变任何国标公式或计算口径**。
 */
export function resolveFuelParams(fuelKey: string, p: FuelDocParams): ResolvedFuelParams {
  const rec = recommendedValues(fuelKey)

  const ncv = p.ncvMeasured ?? rec.ncvKjPerKg ?? null

  // 关键：只用实测 NCV 推导 CC（见文件头约束 2）
  const derivedCc = p.ncvMeasured !== null
    ? deriveCarbonContent(p.carbonPercent, p.ncvMeasured)
    : null
  const cc = p.ccManual ?? derivedCc ?? rec.ccTcPerTj ?? null

  const ashRate = resolveOxidationRate(fuelKey, p.ashOf)
  const of = p.ofManual ?? ashRate.value ?? null

  return {
    ncv,
    cc,
    of,
    sources: {
      ncv: p.ncvMeasured !== null ? 'measured' : (rec.ncvKjPerKg !== null ? 'table1' : 'required'),
      cc: p.ccManual !== null
        ? 'manual'
        : (derivedCc !== null ? 'derived' : (rec.ccTcPerTj !== null ? 'table1' : 'required')),
      of: p.ofManual !== null
        ? 'manual'
        : (ashRate.value !== null ? ashRate.source : 'required'),
    },
    derivedCc,
    oxidationRateNote: ashRate.note,
  }
}

/** 煤的合理 CC 区间 (tC/TJ)，用于合理性提示（非国标强制值） */
export const COAL_CC_TYPICAL_RANGE: [number, number] = [20, 35]

/**
 * 判断推导出的含碳量是否落在煤的常见区间。
 *
 * 仅对煤生效——其他燃料有表 1 推荐值，偏差由推荐值本身保证。
 * 常见根因：把「58.2%」填成「0.582」，或发热量用 GJ/t 而非 kJ/kg。
 */
export function isCoalCcReasonable(fuelKey: string, cc: number | null): boolean {
  if (cc === null) return true
  // 注意：这里必须用**表 1 燃料键**判断，不能再过一遍 fuelTableKey——
  // 后者是「界面小节 → 表 1 行」的映射，对未知 key 会回落 rawCoal，
  // 会把原油等非煤燃料误判成煤。
  const tableKey = isUiSectionKey(fuelKey) ? fuelTableKey(fuelKey) : fuelKey
  if (tableKey !== 'rawCoal') return true
  const [min, max] = COAL_CC_TYPICAL_RANGE
  return cc >= min && cc <= max
}

/** 判断传入的 key 是否是界面小节名（而非表 1 燃料键） */
function isUiSectionKey(key: string): boolean {
  return key in FUEL_TABLE_KEY
}

/** 某个小节是否填了任何单据值（用于表单完整性校验） */
export function hasDocInputs(p: FuelDocParams): boolean {
  return [p.fc, p.carbonPercent, p.ncvMeasured, p.ccManual, p.ofManual]
    .some((value) => value !== null)
}
