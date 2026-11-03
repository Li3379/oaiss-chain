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
  /**
   * 成交审核态：null/0=无需审核，1=待审核，2=已通过，3=已驳回。
   *
   * <p>W3 起 P2P 确认与拍卖同口径走风控分流：命中时交易停在待审核，
   * 配额与碳币仍在冻结态。前端据此区分「已完成」与「已提交审核」。</p>
   */
  auditStatus?: number | null
}

export interface TradePageResult {
  items: TradeResponse[]
  total: number
  page: number
  size: number
  totalPages: number
}
