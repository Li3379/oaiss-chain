/** 交易请求 */
export interface TradeRequest {
  tradeType: number
  buyerId?: number
  sellerId?: number
  quantity: number
  unitPrice: number
  reportId?: number
  remark?: string
}

export type TradeIdentityFilter = 'buyer' | 'seller'

export interface MyTradesQuery {
  pageNum?: number
  pageSize?: number
  tradeType?: number
  status?: number
  tradeNo?: string
  keyword?: string
  identity?: TradeIdentityFilter
  startTime?: string
  endTime?: string
}

/** 交易响应 */
export interface TradeResponse {
  id: number
  tradeNo: string
  tradeType: number
  tradeTypeText: string
  sellerId: number
  sellerName: string
  buyerId: number
  buyerName: string
  quantity: number
  unitPrice: number
  totalAmount: number
  reportId: number
  status: number
  statusText: string
  remark: string
  blockchainTxHash: string
  completedAt: string
  createdAt: string
}

export interface TradePageResult {
  items: TradeResponse[]
  total: number
  page: number
  size: number
  totalPages: number
}
