/**
 * ==============================================================================
 * 【API 模块】：第三方专业核查员工作台与资质管理 (Reviewer API)
 * 【对应后端】：com.oaiss.chain.controller.ReviewerController (/reviewer)
 * 【主要服务页面】：
 *   - 碳排放审核列表: views/auditor/AuditList.vue (/auditor/audit/list)
 *   - 审核历史归档: views/auditor/ReviewHistory.vue (/auditor/review/history)
 * ==============================================================================
 */
import request from './request'
import type {
  CarbonReportResponse,
  PageRequest,
  PagedItems,
  ReviewerInfoResponse,
  ReviewerQualificationResponse,
  ReviewerStatisticsResponse,
} from '../types'

/**
 * 【业务功能】：获取当前登录核查员的个人执业档案、所属机构与擅长行业
 * 【对应前端页面】：AuditList.vue (/auditor/audit/list), ReviewHistory.vue
 * 【界面触发操作】：审核员工作台顶部核查员身份信息条展示
 * 【后端对应端点】：ReviewerController -> GET /reviewer/info
 */
export function getReviewerInfo(): Promise<ReviewerInfoResponse> {
  return request.get('/reviewer/info')
}

/**
 * 【业务功能】：查询待当前核查员初审的排队碳核算报告任务池
 * 【对应前端页面】：AuditList.vue (/auditor/audit/list)
 * 【界面触发操作】：进入审核工作台，加载“待初审报告”表格，支持企业筛选与排序
 * 【后端对应端点】：ReviewerController -> GET /reviewer/reports/pending
 */
export function getPendingReports(params?: PageRequest): Promise<PagedItems<CarbonReportResponse>> {
  return request.get('/reviewer/reports/pending', { params })
}

/**
 * 【业务功能】：查询核查员个人经办的历史审核报告归档记录与评审决议
 * 【对应前端页面】：ReviewHistory.vue (/auditor/review/history)
 * 【界面触发操作】：进入审核历史页面，表格展示已通过或已驳回的历史工单记录
 * 【后端对应端点】：ReviewerController -> GET /reviewer/history
 */
export function getReviewHistory(params?: PageRequest): Promise<PagedItems<CarbonReportResponse>> {
  return request.get('/reviewer/history', { params })
}

/**
 * 【业务功能】：查询核查员本人的专业执业资格证书（Reviewer Qualification）及生效状态
 * 【对应前端页面】：ReviewHistory.vue, AuditList.vue
 * 【界面触发操作】：查看个人执业执照有效期与授权行业领域
 * 【后端对应端点】：ReviewerController -> GET /reviewer/qualification/my
 */
export function getMyReviewerQualification(): Promise<ReviewerQualificationResponse[]> {
  return request.get('/reviewer/qualification/my')
}

/**
 * 【业务功能】：统计核查员的工作量与绩效指标（已审报告数、初审通过率、平均审核时长）
 * 【对应前端页面】：AuditList.vue (/auditor/audit/list)
 * 【界面触发操作】：审核页面顶部统计卡片（已审核总数、待办任务数、通过率等）
 * 【后端对应端点】：ReviewerController -> GET /reviewer/statistics
 */
export function getStatistics(): Promise<ReviewerStatisticsResponse> {
  return request.get('/reviewer/statistics')
}
