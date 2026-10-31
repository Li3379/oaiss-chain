export interface CoalOxidationRateRequest {
  slagWeight: number
  slagCarbonContent: number
  flyAshWeight: number
  flyAshCarbonContent: number
  dustRemovalEfficiency?: number
  coalConsumption: number
  netCalorificValue: number
  carbonContent: number
}

/** Power generation calculation request — flat structure matching backend DTO */
export interface PowerGenerationCalculationRequest {
  rawCoalFc: number | null
  rawCoalNcv: number | null
  rawCoalCc: number | null
  rawCoalOf: number | null
  cleanedCoalFc: number | null
  cleanedCoalNcv: number | null
  cleanedCoalCc: number | null
  cleanedCoalOf: number | null
  otherWashedCoalFc: number | null
  otherWashedCoalNcv: number | null
  otherWashedCoalCc: number | null
  otherWashedCoalOf: number | null
  briquetteFc: number | null
  briquetteNcv: number | null
  briquetteCc: number | null
  briquetteOf: number | null
  otherCoalFc: number | null
  otherCoalNcv: number | null
  otherCoalCc: number | null
  otherCoalOf: number | null
  carbonateConsumed: number | null
  desulfEmissionFactor: number | null
  desulfConversionRate: number | null
  /** 净购入电量 AD电 (MWh) — 国标式第三项，历史上完全缺失 */
  purchasedElectricity?: number | null
  /** 区域电网排放因子 EF电 (tCO2/MWh) */
  electricityEmissionFactor?: number | null
  reportingYear: number
  enterpriseName: string
}

/** Fuel emission detail */
export interface FuelEmissionDetail {
  fuelType: string
  fuelConsumption: number
  netCalorificValue: number
  carbonContent: number
  oxidationRate: number
  emission: number
}

/** Power generation calculation response */
export interface PowerGenerationCalculationResponse {
  totalEmission: number
  combustionEmission: number
  /** 净购入电力间接排放 (tCO2) — 国标式第三项，属范围二 */
  purchasedElectricityEmission?: number
  desulfurizationEmission: number
  fuelDetails: FuelEmissionDetail[]
  reportingYear: string
  enterpriseName: string
  formulaReference: string
  calculatedAt: string
}

/** Power grid calculation request */
/**
 * 电网计算请求（国标净电量式，D4）。
 *
 * 公式：E = (上网 + 输入 − 输出 − 售电) × EF + SF6
 *
 * 旧字段 transmissionVolume / lineLossRate / importEmissionFactor 已弃用：
 * 后端仍接受（标 @Deprecated）但不再参与计算，前端也不再提交。
 */
export interface PowerGridCalculationRequest {
  /** EF电网 (tCO2/MWh) */
  gridEmissionFactor: number
  /** EL上网 (MWh) */
  generationVolume: number | null
  /** EL输入 (MWh) */
  importedElectricity: number | null
  /** EL输出 (MWh) */
  exportedElectricity: number | null
  /** EL售电 (MWh) — 国标式第四项 */
  soldElectricity: number | null
  /** SF6 四项 (kg) */
  recCapacity?: number | null
  recRecovery?: number | null
  repCapacity?: number | null
  repRecovery?: number | null
  reportingYear: number
  enterpriseName: string
}

/** Power grid calculation response */
export interface PowerGridCalculationResponse {
  totalEmission: number
  transmissionLossEmission: number
  importedEmission: number
  /** SF6 绝缘设备泄漏排放 (tCO2e)。后端一直有计算，历史上类型缺此字段，结果面板无法展示。 */
  sf6Emission?: number
  /** 净电量为负（售电 > 供电）时为 true，总量已按 0 计 */
  netElectricityNegative?: boolean
  transmissionLoss: number
  formulaReference: string
  reportingYear: string
  enterpriseName: string
  calculatedAt: string
}