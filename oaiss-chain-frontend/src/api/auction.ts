/**
 * ==============================================================================
 * 【API 模块】：双向连续竞价拍卖撮合引擎与AI采购顾问 (Double Auction API)
 * 【对应后端】：com.oaiss.chain.controller.DoubleAuctionController (/auction)
 * 【主要服务页面】：
 *   - 双向拍卖大厅: views/enterprise/TradingMarket.vue (/enterprise/trading/market)
 *   - 订单管理页面: views/enterprise/OrdersManage.vue (/enterprise/orders/manage)
 * ==============================================================================
 */
import request from './request'
import type {
  AuctionOrderPage,
  AuctionOrderRequest,
  AuctionOrderResponse,
  MatchingResultPage,
  MatchingResultResponse,
  PageRequest,
} from '../types'

/**
 * 【业务功能】：企业提交碳配额双向竞价买入挂单（买盘申报价与申购量）
 * 【对应前端页面】：TradingMarket.vue (/enterprise/trading/market)
 * 【界面触发操作】：在“买入配额”面板输入最高买入限价与买入吨数后，点击【确认提交买单】按钮
 * 【后端对应端点】：DoubleAuctionController -> POST /auction/buy
 */
export function submitBuyOrder(data: AuctionOrderRequest): Promise<AuctionOrderResponse> {
  return request.post('/auction/buy', data)
}

/**
 * 【业务功能】：企业提交碳配额双向竞价卖出挂单（卖盘出让价与出让量）
 * 【对应前端页面】：TradingMarket.vue (/enterprise/trading/market)
 * 【界面触发操作】：在“卖出配额”面板输入最低出让限价与出让吨数后，点击【确认提交卖单】按钮
 * 【后端对应端点】：DoubleAuctionController -> POST /auction/sell
 */
export function submitSellOrder(data: AuctionOrderRequest): Promise<AuctionOrderResponse> {
  return request.post('/auction/sell', data)
}

/**
 * 【业务功能】：查询当前拍卖市场的公开订单簿（买一至买五、卖一至卖五盘口深度）
 * 【对应前端页面】：TradingMarket.vue (/enterprise/trading/market)
 * 【界面触发操作】：大厅实时渲染红绿盘口买卖档位深度图表与订单簿队列
 * 【后端对应端点】：DoubleAuctionController -> GET /auction/orders
 */
export function getAuctionOrders(params?: PageRequest): Promise<AuctionOrderPage> {
  return request.get('/auction/orders', { params })
}

/**
 * 【业务功能】：查询当前登录企业提交的拍卖委托挂单列表与成交状态
 * 【对应前端页面】：TradingMarket.vue (我的挂单Tab), OrdersManage.vue
 * 【界面触发操作】：查看本人当前进行中与已出清的历史拍卖报单
 * 【后端对应端点】：DoubleAuctionController -> GET /auction/my-orders
 */
export function getMyOrders(params?: PageRequest): Promise<AuctionOrderPage> {
  return request.get('/auction/my-orders', { params })
}

/**
 * 【业务功能】：查询拍卖市场的历史撮合成交明细流水（出清价格、撮合成交量、成交时间）
 * 【对应前端页面】：TradingMarket.vue (成交历史Tab)
 * 【界面触发操作】：查看拍卖市场近期撮合成功的成交流水记录
 * 【后端对应端点】：DoubleAuctionController -> GET /auction/results
 */
export function getMatchResults(params?: PageRequest): Promise<MatchingResultPage> {
  return request.get('/auction/results', { params })
}

/**
 * 【业务功能】：管理员或撮合定时器触发双向拍卖撮合算法执行（K-Double Auction机制出清）
 * 【对应前端页面】：TradingMarket.vue (或系统自动化撮合服务)
 * 【界面触发操作】：点击【执行撮合】按钮手动出清或由撮合守护线程自动触发
 * 【后端对应端点】：DoubleAuctionController -> POST /auction/match
 */
export function executeMatching(): Promise<MatchingResultResponse[]> {
  return request.post('/auction/match')
}

export interface ProcurementAdviceResponse {
  shortfall_tons: number
  risk_level: string
  estimated_cost: number
  advice: string
  action_items: string[]
  model_used: string
}

/**
 * 【业务功能】：调用 AI 智能采购 Copilot 生成企业履约缺口诊断与采购策略建议
 * 【对应前端页面】：TradingMarket.vue (/enterprise/trading/market)
 * 【界面触发操作】：点击市场右上角【AI 智能采购助手】唤出建议卡片
 * 【后端对应端点】：CopilotController -> POST /copilot/procurement-advice（后端统一携带 ML 密钥转发）
 */
export function getProcurementAdvice(payload: {
  current_emission: number
  current_quota: number
  current_market_price?: number
  enterprise_name?: string
}): Promise<ProcurementAdviceResponse> {
  return request.post('/copilot/procurement-advice', payload)
}
