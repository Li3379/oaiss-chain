import type { PagedItems } from './api'

/** Request payload for auction buy/sell orders. */
export interface AuctionOrderRequest {
  direction: number
  quantity: number
  price: number
}

/** Auction order returned by backend auction endpoints. */
export interface AuctionOrderResponse {
  id: number
  orderNo: string
  userId: number
  direction: number
  directionText: string
  quantity: number
  price: number
  matchedQuantity: number
  remainingQuantity: number
  status: number
  statusText: string
  settlementPrice: number
  matchedAt: string
  createdAt: string
}

/** Matching result returned by the auction matching endpoints. */
export interface MatchingResultResponse {
  id: number
  matchNo: string
  buyOrderId: number
  sellOrderId: number
  buyerId: number
  sellerId: number
  buyerName: string
  sellerName: string
  matchedQuantity: number
  settlementPrice: number
  totalAmount: number
  status: number
  statusText: string
  transactionId: number
  settledAt: string
  createdAt: string
}

export type AuctionOrderPage = PagedItems<AuctionOrderResponse>
export type MatchingResultPage = PagedItems<MatchingResultResponse>
