/**
 * ==============================================================================
 * 【API 模块】：全站多维数据综合搜索与市场大盘总览 (Search API)
 * 【对应后端】：com.oaiss.chain.controller.SearchController (/search)
 * 【主要服务页面】：
 *   - 官网首页与综合看板: views/OfficialHome.vue, CompanyDashboard.vue
 *   - 市场行情与搜索中心: views/enterprise/TradingMarket.vue
 * ==============================================================================
 */
import request from './request'
import type { CarbonReportSummary, TradeSummary, MarketOverview, PageRequest } from '../types'

/**
 * 【业务功能】：多条件组合全文检索碳核算报告（支持关键字、企业ID与状态综合模糊匹配）
 * 【对应前端页面】：SystemCarbon.vue, Monitor.vue
 * 【界面触发操作】：输入全局关键词检索相关历史报告
 * 【后端对应端点】：SearchController -> GET /search/reports
 */
export function searchReports(params?: PageRequest): Promise<CarbonReportSummary[]> {
  return request.get('/search/reports', { params })
}

/**
 * 【业务功能】：多维度组合检索全市场碳交易流水记录（支持买卖双方、交易类型、成交状态过滤）
 * 【对应前端页面】：OrdersManage.vue, TradingMarket.vue
 * 【界面触发操作】：搜索市场历史成交委托与履约明细
 * 【后端对应端点】：SearchController -> GET /search/trades
 */
export function searchTrades(params?: PageRequest): Promise<TradeSummary[]> {
  return request.get('/search/trades', { params })
}

/**
 * 【业务功能】：获取碳交易市场全网宏观大盘统计数据（全网累计交易量、成交均价、累计核证减排量）
 * 【对应前端页面】：OfficialHome.vue (/official-home), CompanyDashboard.vue
 * 【界面触发操作】：进入官网首页或企业综合看板，加载全网大盘实时概览卡片
 * 【后端对应端点】：SearchController -> GET /search/market-overview
 */
export function getMarketOverview(): Promise<MarketOverview> {
  return request.get('/search/market-overview', { suppressErrorMessage: true })
}
