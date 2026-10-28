/** 排放评级请求 */
export interface EmissionRatingRequest {
  enterpriseId: number
  year: string
  totalEmission: number
  revenue?: number
  ratedBy?: number
}

/** 排放评级实体 */
export interface EmissionRating {
  id: number
  enterpriseId: number
  ratingYear: string
  totalEmission: number
  emissionIntensity: number
  ratingLevel: string
  ratingScore: number
  percentileRank: number
  reductionRatio: number
  ratedBy: number
  remark: string
  createdAt: string
  updatedAt: string
  deleted: boolean
}

/** 碳排放预测请求 */
export interface CarbonPredictionRequest {
  enterpriseId: number
  predictMonths?: number
}

/** 预测数据点 */
export interface PredictionPoint {
  period: string
  predictedEmission: number
}

/** 碳排放预测响应 */
export interface CarbonPredictionResponse {
  enterpriseId: number
  confidence: number
  message: string
  predictions: PredictionPoint[]
  generatedAt: string
}
