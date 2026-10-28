/** Market forecast data point (derived from parallel lists) */
export interface MarketForecastDataPoint {
  date: string
  price: number
  lowerBound: number
  upperBound: number
}

/** Market forecast response from backend (parallel lists format) */
export interface MarketForecastResponse {
  forecastDates: string[]
  forecastPrices: number[]
  lowerBound: number[]
  upperBound: number[]
  trend: string
  modelVersion: string
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
