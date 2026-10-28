/**
 * ==============================================================================
 * 【API 模块】：企业碳效评级码与排放时序预测 (Emission API)
 * 【对应后端】：com.oaiss.chain.controller.EmissionController (/emission)
 * 【主要服务页面】：
 *   - 排放数据与行业对标: views/enterprise/EmissionData.vue (/enterprise/emission/data)
 *   - 企业综合数据大屏: views/enterprise/CompanyDashboard.vue (/enterprise/company/dashboard)
 * ==============================================================================
 */
import request from './request'
import type { EmissionRating, EmissionRatingRequest, CarbonPredictionRequest, CarbonPredictionResponse, PageRequest } from '../types'
import { t } from '@/i18n'

/**
 * 【业务功能】：查询当前企业的历史碳效评级记录（碳效码 A~E 级评定结果）
 * 【对应前端页面】：EmissionData.vue (/enterprise/emission/data), CompanyDashboard.vue
 * 【界面触发操作】：进入排放数据页面自动加载碳效码等级勋章与各项能效得分
 * 【后端对应端点】：EmissionController -> GET /emission/my-rating
 */
export function getMyRating(): Promise<EmissionRating[]> {
  return request.get('/emission/my-rating')
}

/**
 * 【业务功能】：查询指定企业的历史碳效评级与能效指标档案
 * 【对应前端页面】：EmissionData.vue, Monitor.vue
 * 【界面触发操作】：查看同行业其他对标企业的历史评级记录
 * 【后端对应端点】：EmissionController -> GET /emission/ratings/{id}
 */
export function getEnterpriseRatings(enterpriseId: number): Promise<EmissionRating[]> {
  return request.get(`/emission/ratings/${enterpriseId}`)
}

/**
 * 【业务功能】：获取指定自然年份的行业碳排放强度与能效排行榜单
 * 【对应前端页面】：EmissionData.vue (/enterprise/emission/data)
 * 【界面触发操作】：切换年份下拉框，展示行业内部排放对标排名表格
 * 【后端对应端点】：EmissionController -> GET /emission/rankings/{year}
 */
export function getIndustryRankings(year: number, params?: PageRequest): Promise<EmissionRating[]> {
  return request.get(`/emission/rankings/${year}`, { params })
}

/**
 * 【业务功能】：基于时间序列回归算法模型预测企业未来周期碳排放总量趋势
 * 【对应前端页面】：EmissionData.vue (/enterprise/emission/data)
 * 【界面触发操作】：在“未来排放趋势预测”卡片选择预测月数后，点击【执行排放量预测】
 * 【后端对应端点】：EmissionController -> POST /emission/predict
 */
export function predictEmission(data: CarbonPredictionRequest): Promise<CarbonPredictionResponse> {
  return request.post('/emission/predict', data)
}

/**
 * 【业务功能】：基于企业实际综合能耗与产值创建评定新的碳效等级记录
 * 【对应前端页面】：EmissionData.vue, AdminSystemCarbon.vue
 * 【界面触发操作】：管理员或系统核算评级服务计算生成新评级
 * 【后端对应端点】：EmissionController -> POST /emission/ratings
 */
export function createRating(data: EmissionRatingRequest): Promise<EmissionRating> {
  if (!data?.enterpriseId) return Promise.reject(new Error(t('emissionData.enterpriseIdRequired')))
  return request.post('/emission/ratings', data)
}
