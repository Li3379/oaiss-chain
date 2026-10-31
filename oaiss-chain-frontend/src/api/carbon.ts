/**
 * ==============================================================================
 * 【API 模块】：碳核算填报、审核流转与区块链存证认证 (Carbon Accounting API)
 * 【对应后端】：com.oaiss.chain.controller.CarbonController (/carbon)
 * 【主要服务页面】：
 *   - 企业端填报: views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload)
 *   - 企业综合大屏: views/enterprise/CompanyDashboard.vue (/enterprise/company/dashboard)
 *   - 核查员初审列表: views/auditor/AuditList.vue (/auditor/audit/list)
 *   - 审核员上链补救: views/admin/VerifyList.vue (/auditor/verify/list)
 *   - 管理员全网监管: views/admin/SystemCarbon.vue (/admin/system/carbon)
 * ==============================================================================
 */
import request from './request'
import type {
  BatchCertifyRequest,
  BatchCertifyResponse,
  CarbonReportRequest,
  CarbonReportResponse,
  PageRequest,
  PageResponse,
} from '../types'
import { ReportStatusEnum } from '../types'

/**
 * 【业务功能】：创建碳排放核算报告草稿
 * 【对应前端页面】：CarbonUpload.vue (/enterprise/carbon/upload)
 * 【界面触发操作】：填报范围一、范围二排碳数据后，点击【保存为草稿】按钮
 * 【后端对应端点】：CarbonController -> POST /carbon/reports
 */
export function createReport(data: CarbonReportRequest): Promise<CarbonReportResponse> {
  if (!data?.title) return Promise.reject(new Error('Report title is required'))
  return request.post('/carbon/reports', data)
}

/**
 * 【业务功能】：将碳排放核算报告正式提交至第三方审核
 * 【对应前端页面】：CarbonUpload.vue (/enterprise/carbon/upload)
 * 【界面触发操作】：点击【提交审核】按钮，自动触发RSA数字签名并流转至审核员任务池
 * 【后端对应端点】：CarbonController -> POST /carbon/reports/{id}/submit
 */
export function submitReport(reportId: number): Promise<CarbonReportResponse> {
  if (!reportId) return Promise.reject(new Error('Report ID is required'))
  return request.post(`/carbon/reports/${reportId}/submit`)
}

/**
 * 【业务功能】：管理员/监管端分页多条件查询全平台碳报告列表
 * 【对应前端页面】：VerifyList.vue (/auditor/verify/list), SystemCarbon.vue (/admin/system/carbon)
 * 【界面触发操作】：进入上链补救页或按状态、企业、年份进行表格检索
 * 【后端对应端点】：CarbonController -> GET /carbon/reports
 */
export function getReportList(params?: PageRequest): Promise<PageResponse<CarbonReportResponse>> {
  return request.get('/carbon/reports', { params })
}

/**
 * 【业务功能】：企业用户查询自己申报的历史碳报告列表
 * 【对应前端页面】：CarbonUpload.vue (/enterprise/carbon/upload), CompanyDashboard.vue
 * 【界面触发操作】：填报页面下方历史报告表格分页加载与状态展示
 * 【后端对应端点】：CarbonController -> GET /carbon/my-reports
 */
export function getMyReports(params?: PageRequest): Promise<PageResponse<CarbonReportResponse>> {
  return request.get('/carbon/my-reports', { params })
}

/**
 * 【业务功能】：删除草稿状态的碳报告
 * 【对应前端页面】：CarbonUpload.vue (/enterprise/carbon/upload)
 * 【界面触发操作】：在草稿状态报告的操作列点击【删除】并二次确认
 * 【后端对应端点】：CarbonController -> DELETE /carbon/reports/{id}
 */
export function deleteReport(reportId: number): Promise<void> {
  if (!reportId) return Promise.reject(new Error('Report ID is required'))
  return request.delete(`/carbon/reports/${reportId}`)
}

/**
 * 【业务功能】：修改草稿或已驳回的碳报告（D13）
 * 【对应前端页面】：views/enterprise/CarbonUpload.vue
 * 【界面触发操作】：报告列表中「草稿」或「已驳回」行点击【修改】
 * 【后端对应端点】：CarbonController -> PUT /carbon/reports/{reportId}
 *
 * 只允许这两种状态——已提交/已通过/已上链的报告不可编辑，后端会拒绝。
 */
export function updateReport(
  reportId: number,
  data: CarbonReportRequest,
): Promise<CarbonReportResponse> {
  if (!reportId) return Promise.reject(new Error('Report ID is required'))
  return request.put(`/carbon/reports/${reportId}`, data)
}

/**
 * 【业务功能】：专业核查员对企业申报的碳排放报告进行初审（通过或驳回）
 * 【对应前端页面】：AuditList.vue (/auditor/audit/list)
 * 【界面触发操作】：审查附件凭证和验签后，点击【通过审核】或【驳回修改】弹窗提交
 * 【后端对应端点】：CarbonController -> POST /carbon/review
 */
export function reviewReport(data: { reportId: number; approved: boolean; comment: string }): Promise<CarbonReportResponse> {
  if (!data?.reportId) return Promise.reject(new Error('Report ID is required'))
  return request.post('/carbon/review', {
    reportId: data.reportId,
    reviewResult: data.approved ? ReportStatusEnum.APPROVED : ReportStatusEnum.REJECTED,
    reviewComment: data.comment,
  })
}

/**
 * 【业务功能】：系统管理员对初审通过的碳报告进行终审认证并触发区块链存证上链
 * 【对应前端页面】：VerifyList.vue (/auditor/verify/list)
 * 【界面触发操作】：在上链补救页单条记录操作列点击【上链补救】按钮
 * 【后端对应端点】：CarbonController -> POST /carbon/certify
 */
export function certifyReport(data: { reportId: number; approved: boolean; comment: string }): Promise<void> {
  if (!data?.reportId) return Promise.reject(new Error('Report ID is required'))
  return request.post('/carbon/certify', {
    reportId: data.reportId,
    reviewResult: data.approved ? ReportStatusEnum.ON_CHAIN : ReportStatusEnum.REJECTED,
    reviewComment: data.comment,
  })
}

/**
 * 【业务功能】：系统管理员批量认证多份碳报告并批量存证上链至 Hyperledger Fabric
 * 【对应前端页面】：VerifyList.vue (/auditor/verify/list)
 * 【界面触发操作】：勾选多个复选框后点击表格上方的【批量上链认证】按钮
 * 【后端对应端点】：CarbonController -> POST /carbon/certify/batch
 */
export function batchCertifyReports(data: BatchCertifyRequest): Promise<BatchCertifyResponse> {
  if (!data?.reportIds?.length) return Promise.reject(new Error('Report IDs are required'))
  return request.post('/carbon/certify/batch', data)
}

/**
 * 【业务功能】：根据报告ID查询单个碳核算报告的完整明细数据与结构化因子
 * 【对应前端页面】：CarbonUpload.vue 详情抽屉, AuditList.vue 审核抽屉
 * 【界面触发操作】：点击报告列表中的【查看详情】按钮
 * 【后端对应端点】：CarbonController -> GET /carbon/reports/{id}
 */
export function getReport(reportId: number): Promise<CarbonReportResponse> {
  if (!reportId) return Promise.reject(new Error('Report ID is required'))
  return request.get(`/carbon/reports/${reportId}`)
}

export interface AiReportAnalysisResponse {
  report_id?: number | string
  summary: string
  key_findings: string[]
  reduction_suggestions: string[]
  full_text: string
  analysis_hash: string
  model_used: string
  /** true 表示报告缺少可用排放量，后端未生成诊断（各分析字段为空） */
  insufficient_data?: boolean
  /** insufficient_data 为 true 时的说明文本 */
  insufficient_data_reason?: string
}

/**
 * 【业务功能】：调用 AI 智能 Copilot 对碳报告排放明细进行大模型综合诊断与减排建议推演
 * 【对应前端页面】：CarbonUpload.vue (/enterprise/carbon/upload)
 * 【界面触发操作】：点击报告操作列的【AI智能分析】按钮，弹出减排建议对话框
 * 【后端对应端点】：CopilotController -> POST /copilot/report-analysis（后端统一携带 ML 密钥转发）
 */
export function getAiReportAnalysis(payload: {
  report_id?: number | string
  enterprise_name?: string
  accounting_period?: string
  total_emission: number
  scope1_emission?: number
  scope2_emission?: number
  desulf_emission?: number
  emission_details?: unknown
}): Promise<AiReportAnalysisResponse> {
  return request.post('/copilot/report-analysis', payload)
}
