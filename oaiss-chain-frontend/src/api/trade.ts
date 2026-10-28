/**
 * ==============================================================================
 * 【API 模块】：点对点（P2P）协商转让与碳交易执行引擎 (Trade API)
 * 【对应后端】：com.oaiss.chain.controller.TradeController (/trade)
 * 【主要服务页面】：
 *   - P2P交易页面: views/enterprise/TradingP2P.vue (/enterprise/trading/p2p)
 *   - 订单管理页面: views/enterprise/OrdersManage.vue (/enterprise/orders/manage)
 *   - 综合数据看板: views/enterprise/CompanyDashboard.vue (/enterprise/company/dashboard)
 * ==============================================================================
 */
import request from './request'
import type { MyTradesQuery, PageRequest, TradePageResult, TradeRequest, TradeResponse } from '../types'

/**
 * 【业务功能】：发起点对点（P2P）定向碳配额协商转让委托
 * 【对应前端页面】：TradingP2P.vue (/enterprise/trading/p2p)
 * 【界面触发操作】：输入目标买方企业ID、转让配额数量与单价后，点击【发起定向转让】按钮
 * 【后端对应端点】：TradeController -> POST /trade/p2p
 */
export function createP2PTrade(data: TradeRequest): Promise<TradeResponse> {
  if (!data?.quantity || data.quantity <= 0) return Promise.reject(new Error('碳配额数量必须大于0'))
  if (!data?.unitPrice || data.unitPrice <= 0) return Promise.reject(new Error('价格必须大于0'))
  return request.post('/trade/p2p', data)
}

/**
 * 【业务功能】：撤销未成交或待对方确认的碳交易订单
 * 【对应前端页面】：OrdersManage.vue (/enterprise/orders/manage), TradingP2P.vue
 * 【界面触发操作】：在活动订单列表中点击【撤单】按钮，确认后释放锁定的配额或资金
 * 【后端对应端点】：TradeController -> POST /trade/{id}/cancel
 */
export function cancelTrade(tradeId: number): Promise<void> {
  if (!tradeId) return Promise.reject(new Error('交易ID不能为空'))
  return request.post(`/trade/${tradeId}/cancel`)
}

/**
 * 【业务功能】：查询当前企业参与的全部交易流水（作为买方或卖方）
 * 【对应前端页面】：OrdersManage.vue, TradingP2P.vue, CompanyDashboard.vue
 * 【界面触发操作】：订单管理页面按交易状态、交易类型（P2P/拍卖）筛选列表
 * 【后端对应端点】：TradeController -> GET /trade/my-trades
 */
export function getMyTrades(params?: MyTradesQuery): Promise<TradePageResult> {
  return request.get('/trade/my-trades', { params })
}

/**
 * 【业务功能】：发起公开市场挂牌拍卖订单
 * 【对应前端页面】：TradingMarket.vue (/enterprise/trading/market)
 * 【界面触发操作】：在交易市场点击【挂牌出让】或【挂牌求购】
 * 【后端对应端点】：TradeController -> POST /trade/auction
 */
export function createAuctionTrade(data: TradeRequest): Promise<TradeResponse> {
  if (!data?.quantity || data.quantity <= 0) return Promise.reject(new Error('碳配额数量必须大于0'))
  if (!data?.unitPrice || data.unitPrice <= 0) return Promise.reject(new Error('价格必须大于0'))
  return request.post('/trade/auction', data)
}

/**
 * 【业务功能】：交易对手方确认成交，触发原子化配额划转与碳币资金结算
 * 【对应前端页面】：TradingP2P.vue (/enterprise/trading/p2p), OrdersManage.vue
 * 【界面触发操作】：买方企业在收到的待确认转让订单卡片上点击【确认承接/支付】
 * 【后端对应端点】：TradeController -> POST /trade/{id}/confirm
 */
export function confirmTrade(tradeId: number): Promise<void> {
  if (!tradeId) return Promise.reject(new Error('交易ID不能为空'))
  return request.post(`/trade/${tradeId}/confirm`)
}

/**
 * 【业务功能】：根据交易ID查询单笔交易详情与清算日志
 * 【对应前端页面】：OrdersManage.vue 订单详情弹窗
 * 【界面触发操作】：点击订单行中的【查看明细】链接
 * 【后端对应端点】：TradeController -> GET /trade/{id}
 */
export function getTrade(tradeId: number): Promise<TradeResponse> {
  if (!tradeId) return Promise.reject(new Error('交易ID不能为空'))
  return request.get(`/trade/${tradeId}`)
}

/**
 * 【业务功能】：查询公开碳交易市场订单列表
 * 【对应前端页面】：TradingMarket.vue, Search.vue
 * 【界面触发操作】：查看市场全景公开挂单
 * 【后端对应端点】：TradeController -> GET /trade/list
 */
export function listTrades(params?: PageRequest): Promise<TradePageResult> {
  return request.get('/trade/list', { params })
}
