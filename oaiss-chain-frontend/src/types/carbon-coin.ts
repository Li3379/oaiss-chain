import type { PagedItems } from './api'

export interface CarbonCoinAccountResponse {
  id: number
  userId: number
  balance: number
  totalRecharged: number
  totalSpent: number
  status: number
}

export interface CarbonCoinRechargeRequest {
  amount: number
  paymentMethod?: number
  remark?: string
}

export interface CarbonCoinTransferRequest {
  counterpartId: number
  amount: number
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
  counterpartId: number | null
  remark: string
  createdAt: string
  updatedAt: string
}

export type CarbonCoinTransactionPage = PagedItems<CarbonCoinTransaction>
