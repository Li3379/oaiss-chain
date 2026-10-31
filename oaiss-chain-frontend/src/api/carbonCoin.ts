/**
 * ==============================================================================
 * 【API 模块】：碳币资产代币钱包与资金流水 (Carbon Coin API)
 * 【对应后端】：com.oaiss.chain.controller.CarbonCoinController (/carbon-coin)
 * 【主要服务页面】：
 *   - 碳币账户页面: views/enterprise/CarbonCoin.vue (/enterprise/carbon-coin/account)
 *   - 企业综合大屏: views/enterprise/CompanyDashboard.vue (/enterprise/company/dashboard)
 *   - 双向拍卖与P2P大厅: views/enterprise/TradingMarket.vue, TradingP2P.vue
 * ==============================================================================
 */
import request from './request'
import type {
  CarbonCoinAccountResponse,
  CarbonCoinRechargeRequest,
  CarbonCoinTransactionPage,
  PageRequest,
} from '../types'

/**
 * 【业务功能】：查询当前企业/用户的碳币钱包账户信息与当前可用/冻结余额
 * 【对应前端页面】：CarbonCoin.vue (/enterprise/carbon-coin/account), CompanyDashboard.vue
 * 【界面触发操作】：进入碳币账户页面自动展示当前碳币总资产余额卡片
 * 【后端对应端点】：CarbonCoinController -> GET /carbon-coin/account
 */
export function getMyAccount(): Promise<CarbonCoinAccountResponse> {
  return request.get('/carbon-coin/account')
}

/**
 * 【业务功能】：分页查询碳币账户收支交易明细
 * 【对应前端页面】：CarbonCoin.vue (/enterprise/carbon-coin/account)
 * 【界面触发操作】：查看“交易明细”表格，展示充值、扣减与交易结算记录
 * 【后端对应端点】：CarbonCoinController -> GET /carbon-coin/transactions
 */
export function getTransactions(params?: PageRequest): Promise<CarbonCoinTransactionPage> {
  return request.get('/carbon-coin/transactions', { params })
}


/**
 * 【业务功能】：向指定用户或企业碳币账户进行充值（资金入账）
 * 【对应前端页面】：CarbonCoin.vue (/enterprise/carbon-coin/account)
 * 【界面触发操作】：点击【充值】按钮输入充值金额并确认
 * 【后端对应端点】：CarbonCoinController -> POST /carbon-coin/recharge
 */
export function recharge(userId: number, amount: number): Promise<CarbonCoinAccountResponse> {
  if (!userId) return Promise.reject(new Error('用户ID不能为空'))
  if (!amount || amount <= 0) return Promise.reject(new Error('充值金额必须大于0'))

  const payload: CarbonCoinRechargeRequest = { amount }
  return request.post(`/carbon-coin/recharge?userId=${userId}`, payload)
}
