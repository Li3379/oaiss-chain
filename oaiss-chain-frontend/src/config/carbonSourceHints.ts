/**
 * 碳核算参数「数据来源」提示。
 *
 * 用途：界面上每个参数旁挂一个 ⓘ，告诉填报人**这个数从哪种单据/设备上取**。
 * 只做「去哪拿」的说明，不改变任何国标定义、公式结构或参数口径。
 *
 * 编写原则：
 *   1. 说单据名，不说抽象概念（「化验单」优于「检测报告」）。
 *   2. 说去哪找（「地磅单」「电费单」「铭牌」），让没有专业背景的人也能对上。
 *   3. 国标无推荐值的项，明确写「必须实测」，不编造参考值。
 *
 * 键名与 i18n `carbonSource.<key>` 对应，zh/en 双侧必须镜像。
 */
export interface CarbonSourceHint {
  /** 一句话说明这个数从哪获取 */
  key: string
}

/**
 * 发电企业（25 参口径）参数来源。
 * 注意：GB/T 32150 表 1 的燃煤行低位发热量与含碳量两格为空，
 * 因此这两项对燃煤是「必须实测」，不能给推荐值。
 */
export const POWER_GEN_SOURCE_HINTS: Record<string, CarbonSourceHint> = {
  fc: { key: 'fc' },
  ncv: { key: 'ncv' },
  cc: { key: 'cc' },
  of: { key: 'of' },
  carbonateConsumed: { key: 'carbonateConsumed' },
  desulfEmissionFactor: { key: 'desulfEmissionFactor' },
  desulfConversionRate: { key: 'desulfConversionRate' },
  purchasedElectricity: { key: 'purchasedElectricity' },
  electricityEmissionFactor: { key: 'electricityEmissionFactor' },
}

/** 电网企业（9 参口径）参数来源 */
export const POWER_GRID_SOURCE_HINTS: Record<string, CarbonSourceHint> = {
  transmissionVolume: { key: 'transmissionVolume' },
  lineLossRate: { key: 'lineLossRate' },
  gridEmissionFactor: { key: 'gridEmissionFactor' },
  generationVolume: { key: 'generationVolume' },
  importedElectricity: { key: 'importedElectricity' },
  exportedElectricity: { key: 'exportedElectricity' },
  importEmissionFactor: { key: 'importEmissionFactor' },
  recCapacity: { key: 'recCapacity' },
  recRecovery: { key: 'recRecovery' },
  repCapacity: { key: 'repCapacity' },
  repRecovery: { key: 'repRecovery' },
}

/** 灰渣反推助手的参数来源 */
export const ASH_HELPER_SOURCE_HINTS: Record<string, CarbonSourceHint> = {
  slagWeight: { key: 'slagWeight' },
  slagCarbonContent: { key: 'slagCarbonContent' },
  flyAshWeight: { key: 'flyAshWeight' },
  flyAshCarbonContent: { key: 'flyAshCarbonContent' },
  dustRemovalEfficiency: { key: 'dustRemovalEfficiency' },
}
/**
 * 参数取值的规则来源（用于「因子可溯源」的第一层）。
 *
 * 四档优先级（高 → 低）：企业实测 > 供应商提供 > 国标表 1 推荐值 > 历史沿用。
 * 该类型同时被 `gbTable1.ts` 的 `recommendedValues()` 复用。
 */
export type RuleSourceType = 'measured' | 'supplier' | 'gbTable1' | 'historical'

export interface RuleSource {
  type: RuleSourceType
  /** 具体出处引用，如「GB/T 32150-2015 表 1」或化验单编号 */
  ref: string
}
