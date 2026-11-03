/** Market forecast data point (derived from parallel lists) */
export interface MarketForecastDataPoint {
  date: string
  price: number
  lowerBound: number
  upperBound: number
  /** 成交量预测（仅供需端点有值，价格类端点为 undefined） */
  volume?: number
}

/** Market forecast response from backend (parallel lists format) */
export interface MarketForecastResponse {
  forecastDates: string[]
  forecastPrices: number[]
  lowerBound: number[]
  upperBound: number[]
  trend: string
  modelVersion: string
  /** 成交量预测序列：仅供需端点填充 */
  forecastVolumes?: number[]
  /** 成交量单位，如 tCO2e */
  forecastVolumeUnit?: string | null
}

/** Enterprise inference response from backend */
export interface EnterpriseInferenceResponse {
  enterpriseId: number
  complianceStatus: string
  confidence: number
  anomalyScore: number
  isAnomaly: boolean
  riskFactors: string[]
  modelVersion: string
}
