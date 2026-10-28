/** 信用分响应 */
export interface CreditScoreResponse {
  id: number
  enterpriseId: number
  enterpriseName: string
  score: number
  level: string
  tradeRestricted: boolean
  accountFrozen: boolean
  lastEvaluatedAt: string
  createdAt: string
}

/** 信用事件响应 */
export interface CreditEventResponse {
  id: number
  enterpriseId: number
  eventType: number
  eventTypeName: string
  eventDescription: string
  pointsChanged: number
  scoreBefore: number
  scoreAfter: number
  relatedReportId: number
  relatedTradeId: number
  triggeredBy: number
  triggeredByName: string
  triggeredAt: string
}

/** 信用扣分请求 */
export interface CreditDeductionRequest {
  enterpriseId: number
  eventType: number
  description: string
  relatedReportId?: number
}
