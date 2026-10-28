/**
 * ==============================================================================
 * 【API 模块】：双碳履约信用评级与风控拦截 (Credit Score API)
 * 【对应后端】：com.oaiss.chain.controller.CreditScoreController (/credit)
 * 【主要服务页面】：
 *   - 信誉评分页面: views/enterprise/CreditScore.vue (/enterprise/credit/score)
 *   - 企业综合大屏: views/enterprise/CompanyDashboard.vue (/enterprise/company/dashboard)
 *   - 项目审核扣分: views/auditor/ProjectReview.vue (/auditor/project/review)
 *   - 证书与交易风控: views/admin/CertificateManage.vue, TradingP2P.vue
 * ==============================================================================
 */
import request from './request'
import type {
  CreditScoreResponse,
  CreditEventResponse,
  CreditDeductionRequest,
  PageRequest,
  PageResponse,
} from '../types'

/**
 * 【业务功能】：查询当前企业的信誉分值、信用评级（AAA~D）与履约维度雷达数据
 * 【对应前端页面】：CreditScore.vue (/enterprise/credit/score), CompanyDashboard.vue
 * 【界面触发操作】：进入信誉评分页面展示综合得分卡片、评级标签及雷达图
 * 【后端对应端点】：CreditScoreController -> GET /credit/my-score
 */
export function getMyScore(): Promise<CreditScoreResponse> {
  return request.get('/credit/my-score')
}

/**
 * 【业务功能】：分页查询当前企业自身的信誉奖惩变更事件流水（扣分/加分原因及分值）
 * 【对应前端页面】：CreditScore.vue (/enterprise/credit/score)
 * 【界面触发操作】：信誉历史事件表格加载，支持按事件类型筛选违约扣分或守约加分
 * 【后端对应端点】：CreditScoreController -> GET /credit/history
 */
export function getScoreHistory(params?: PageRequest & { eventType?: number }): Promise<PageResponse<CreditEventResponse>> {
  return request.get('/credit/history', { params })
}

/**
 * 【业务功能】：查询全市场企业信誉排名榜单
 * 【对应前端页面】：CreditScore.vue (/enterprise/credit/score), TradingP2P.vue, CertificateManage.vue
 * 【界面触发操作】：信誉大厅右侧“行业信用排行榜”列表展示
 * 【后端对应端点】：CreditScoreController -> GET /credit/ranking
 */
export function getScoreRanking(params?: PageRequest): Promise<PageResponse<CreditScoreResponse>> {
  return request.get('/credit/ranking', { params })
}

/**
 * 【业务功能】：查询指定企业的信誉积分与信用等级信息
 * 【对应前端页面】：AuditList.vue, TradingP2P.vue
 * 【界面触发操作】：查看交易对手或被审核企业的信用画像
 * 【后端对应端点】：CreditScoreController -> GET /credit/{id}
 */
export function getEnterpriseScore(enterpriseId: number): Promise<CreditScoreResponse> {
  if (!enterpriseId) return Promise.reject(new Error('Enterprise ID is required'))
  return request.get(`/credit/${enterpriseId}`)
}

/**
 * 【业务功能】：查询指定企业的历史信誉奖惩事件记录
 * 【对应前端页面】：ProjectReview.vue, Monitor.vue
 * 【界面触发操作】：审核员或监管员审查企业历史违约扣分记录
 * 【后端对应端点】：CreditScoreController -> GET /credit/{id}/history
 */
export function getEnterpriseCreditHistory(
  enterpriseId: number,
  params?: PageRequest & { eventType?: number },
): Promise<PageResponse<CreditEventResponse>> {
  if (!enterpriseId) return Promise.reject(new Error('Enterprise ID is required'))
  return request.get(`/credit/${enterpriseId}/history`, { params })
}

/**
 * 【业务功能】：审核员或监管方对弄虚作假、逾期未履约企业执行违规扣减信誉积分处罚
 * 【对应前端页面】：ProjectReview.vue (/auditor/project/review), Monitor.vue
 * 【界面触发操作】：项目核证审核驳回时，勾选“同步扣减企业信誉分”并填报扣除分值
 * 【后端对应端点】：CreditScoreController -> POST /credit/deduct
 */
export function deductPoints(data: CreditDeductionRequest): Promise<CreditScoreResponse> {
  if (!data?.enterpriseId) return Promise.reject(new Error('Enterprise ID is required'))
  if (!data?.eventType) return Promise.reject(new Error('Credit event type is required'))
  return request.post('/credit/deduct', data)
}

/**
 * 【业务功能】：对按时履约、核算规范或有突出碳中和贡献的企业给予奖励加分
 * 【对应前端页面】：SystemCarbon.vue, CertificateManage.vue
 * 【界面触发操作】：管理员或监管端点击【信用奖励加分】弹窗操作
 * 【后端对应端点】：CreditScoreController -> POST /credit/bonus
 */
export function addBonus(
  data: { enterpriseId: number; points: number; reason?: string; description?: string },
): Promise<CreditScoreResponse> {
  if (!data?.enterpriseId) return Promise.reject(new Error('Enterprise ID is required'))
  if (!data?.points || data.points <= 0) return Promise.reject(new Error('Bonus points must be greater than 0'))

  const description = data.description ?? data.reason
  if (!description) return Promise.reject(new Error('Bonus description is required'))

  return request.post('/credit/bonus', null, {
    params: {
      enterpriseId: data.enterpriseId,
      points: data.points,
      description,
    },
  })
}

/**
 * 【业务功能】：基于企业履约率与历史行为重新计算并评定信用等级
 * 【对应前端页面】：CreditScore.vue
 * 【界面触发操作】：点击【重新评估信用等级】按钮
 * 【后端对应端点】：CreditScoreController -> POST /credit/evaluate/{id}
 */
export function evaluateLevel(enterpriseId: number): Promise<CreditScoreResponse> {
  if (!enterpriseId) return Promise.reject(new Error('Enterprise ID is required'))
  return request.post(`/credit/evaluate/${enterpriseId}`)
}

/**
 * 【业务功能】：获取当前被列入交易受限名单的企业列表（信用分低于警戒阈值）
 * 【对应前端页面】：CertificateManage.vue (/admin/certificates)
 * 【界面触发操作】：证书管理页面切换至“交易受限名单”Tab
 * 【后端对应端点】：CreditScoreController -> GET /credit/restricted
 */
export function getRestrictedEnterprises(): Promise<CreditScoreResponse[]> {
  return request.get('/credit/restricted')
}

/**
 * 【业务功能】：获取账户被完全冻结的企业清单（严重失信企业）
 * 【对应前端页面】：CertificateManage.vue (/admin/certificates), SystemUsers.vue
 * 【界面触发操作】：查看黑名单冻结企业
 * 【后端对应端点】：CreditScoreController -> GET /credit/frozen
 */
export function getFrozenEnterprises(): Promise<CreditScoreResponse[]> {
  return request.get('/credit/frozen')
}

/**
 * 【业务功能】：碳交易前置风控拦截校验（检查企业信誉等级是否允许挂牌与撮合）
 * 【对应前端页面】：TradingMarket.vue, TradingP2P.vue
 * 【界面触发操作】：在提交买卖订单前调用防风控穿透，拦截低信誉企业
 * 【后端对应端点】：CreditScoreController -> GET /credit/check-permission/{id}
 */
export function checkTradePermission(enterpriseId: number): Promise<boolean> {
  if (!enterpriseId) return Promise.reject(new Error('Enterprise ID is required'))
  return request.get(`/credit/check-permission/${enterpriseId}`)
}
