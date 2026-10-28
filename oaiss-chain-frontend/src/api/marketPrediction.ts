/**
 * ==============================================================================
 * 【API 模块】：AI 碳市场量化预测与宏观行情推演 (Market Prediction API)
 * 【对应后端】：com.oaiss.chain.controller.MarketPredictionController (/ai/market)
 * 【主要服务页面】：
 *   - AI市场预测页面: views/enterprise/MarketPrediction.vue (/enterprise/market-prediction)
 * ==============================================================================
 */
import request from './request'
import type { MarketForecastResponse } from '@/types/ai'

/**
 * 【业务功能】：基于 Prophet / 时间序列算法预测未来周期碳市场整体交易活跃度与大盘走势
 * 【对应前端页面】：MarketPrediction.vue (/enterprise/market-prediction)
 * 【界面触发操作】：在“市场行情趋势预测”面板选择预测周期（7/30/90/180天），图表自动更新
 * 【后端对应端点】：MarketPredictionController -> POST /ai/market/trend
 */
export function getMarketTrend(horizonDays: number = 30): Promise<MarketForecastResponse> {
  return request.post('/ai/market/trend', null, { params: { horizonDays }, suppressErrorMessage: true })
}

/**
 * 【业务功能】：基于 XGBoost 回归模型预测未来碳配额成交均价走势与置信区间波动带
 * 【对应前端页面】：MarketPrediction.vue (/enterprise/market-prediction)
 * 【界面触发操作】：在“配额价格预测”面板查看未来 30 天预测价格曲线与高低价格区间
 * 【后端对应端点】：MarketPredictionController -> POST /ai/market/price
 */
export function getMarketPrice(horizonDays: number = 30): Promise<MarketForecastResponse> {
  return request.post('/ai/market/price', null, { params: { horizonDays }, suppressErrorMessage: true })
}

/**
 * 【业务功能】：预测未来周期全市场碳配额买入需求与卖出供应的供需缺口平衡指数
 * 【对应前端页面】：MarketPrediction.vue (/enterprise/market-prediction)
 * 【界面触发操作】：在“供需平衡分析”卡片查看配额缺口/盈余指数柱状图
 * 【后端对应端点】：MarketPredictionController -> POST /ai/market/supply-demand
 */
export function getSupplyDemand(horizonDays: number = 30): Promise<MarketForecastResponse> {
  return request.post('/ai/market/supply-demand', null, { params: { horizonDays }, suppressErrorMessage: true })
}
