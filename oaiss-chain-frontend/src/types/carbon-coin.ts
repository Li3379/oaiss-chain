import type { PagedItems } from './api'

export interface CarbonCoinAccountResponse {
  id: number
  userId: number
  balance: number
  frozenBalance?: number
  /** 累计充值——充值端点已下线，该值不再增长（历史遗留） */
  totalRecharged: number
  /** 累计发放——减排成果发放的核心指标，回收不减少 */
  totalIssued?: number
  totalSpent: number
  status: number
}

export interface CarbonCoinRechargeRequest {
  amount: number
  paymentMethod?: number
  remark?: string
}

export interface CarbonCoinTransaction {
  id: number
  txNo: string
  userId: number
  txType: number
  amount: number
  balanceBefore: number
  balanceAfter: number
  relatedQuota: number | null
  relatedTradeId: number | null
  remark: string
  createdAt: string
  updatedAt: string
}

export type CarbonCoinTransactionPage = PagedItems<CarbonCoinTransaction>
