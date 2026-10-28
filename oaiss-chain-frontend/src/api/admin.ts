/**
 * ==============================================================================
 * 【API 模块】：系统治理、用户启停、配置与证书准入签发 (Admin API)
 * 【对应后端】：com.oaiss.chain.controller.AdminController (/admin)
 * 【主要服务页面】：
 *   - 用户管理: views/admin/SystemUsers.vue (/admin/system/users)
 *   - 证书管理: views/admin/CertificateManage.vue (/admin/certificates)
 *   - 数据统计: views/admin/DataStatistics.vue (/admin/data/statistics)
 *   - 系统配置: views/admin/SystemConfig.vue (/admin/system/config)
 * ==============================================================================
 */
import request from './request'
import type {
  AdminConfigResponse,
  AdminDashboardResponse,
  AdminPermissionResponse,
  AdminStatisticsResponse,
  AdminUserPage,
  EnterpriseAdmissionPage,
  EnterpriseAdmissionResponse,
  PageRequest,
  ReviewerQualificationPage,
  ReviewerQualificationResponse,
} from '../types'

/**
 * 【业务功能】：分页多条件查询系统全量用户列表（支持按角色、启用状态、用户名检索）
 * 【对应前端页面】：SystemUsers.vue (/admin/system/users)
 * 【界面触发操作】：进入用户管理页面加载用户表格，或在顶部筛选栏进行检索
 * 【后端对应端点】：AdminController -> GET /admin/users
 */
export function getUserList(params?: PageRequest): Promise<AdminUserPage> {
  return request.get('/admin/users', { params })
}

/**
 * 【业务功能】：修改系统用户状态（启用/禁用或冻结账号）
 * 【对应前端页面】：SystemUsers.vue (/admin/system/users)
 * 【界面触发操作】：在用户表格操作列点击【禁用】或【启用】开关，弹出确认框后提交
 * 【后端对应端点】：AdminController -> PUT /admin/users/{userId}/status
 */
export function updateUserStatus(userId: number, status: number): Promise<void> {
  return request.put(`/admin/users/${userId}/status`, null, { params: { status } })
}

/**
 * 【业务功能】：获取系统用户角色构成统计与活跃组织宏观分布指标
 * 【对应前端页面】：DataStatistics.vue (/admin/data/statistics)
 * 【界面触发操作】：统计数据页面渲染“用户角色构成饼图”与各项组织统计卡片
 * 【后端对应端点】：AdminController -> GET /admin/statistics
 */
export function getStatistics(): Promise<AdminStatisticsResponse> {
  return request.get('/admin/statistics')
}

/**
 * 【业务功能】：分页查询排碳企业准入资格证书列表（Enterprise Admission）
 * 【对应前端页面】：CertificateManage.vue (/admin/certificates)
 * 【界面触发操作】：证书管理页面“企业准入资质”Tab加载表格
 * 【后端对应端点】：AdminController -> GET /admin/enterprise-admission
 */
export function getEnterpriseAdmissionList(params?: PageRequest): Promise<EnterpriseAdmissionPage> {
  return request.get('/admin/enterprise-admission', { params })
}

/**
 * 【业务功能】：审核并向合规排碳企业正式签发市场准入证书
 * 【对应前端页面】：CertificateManage.vue (/admin/certificates)
 * 【界面触发操作】：在企业准入列表操作列点击【签发准入证书】按钮
 * 【后端对应端点】：AdminController -> POST /admin/enterprise-admission/{id}/issue
 */
export function issueEnterpriseAdmission(enterpriseId: number): Promise<EnterpriseAdmissionResponse> {
  return request.post(`/admin/enterprise-admission/${enterpriseId}/issue`)
}

/**
 * 【业务功能】：吊销或作废严重违规/退出市场的企业准入资格证书
 * 【对应前端页面】：CertificateManage.vue (/admin/certificates)
 * 【界面触发操作】：在企业准入列表操作列点击【吊销证书】并二次确认
 * 【后端对应端点】：AdminController -> DELETE /admin/enterprise-admission/{id}
 */
export function revokeEnterpriseAdmission(enterpriseId: number): Promise<void> {
  return request.delete(`/admin/enterprise-admission/${enterpriseId}`)
}

/**
 * 【业务功能】：分页查询第三方审核员专业执业资格证书列表（Reviewer Qualification）
 * 【对应前端页面】：CertificateManage.vue (/admin/certificates)
 * 【界面触发操作】：证书管理页面“审核员执业资格”Tab加载表格
 * 【后端对应端点】：AdminController -> GET /admin/reviewer-qualification
 */
export function getReviewerQualificationList(params?: PageRequest): Promise<ReviewerQualificationPage> {
  return request.get('/admin/reviewer-qualification', { params })
}

/**
 * 【业务功能】：向通过资质考核的核查员签发专业执业资格证书
 * 【对应前端页面】：CertificateManage.vue (/admin/certificates)
 * 【界面触发操作】：在审核员资格列表操作列点击【签发资格证】按钮
 * 【后端对应端点】：AdminController -> POST /admin/reviewer-qualification/{id}/issue
 */
export function issueReviewerQualification(reviewerId: number): Promise<ReviewerQualificationResponse> {
  return request.post(`/admin/reviewer-qualification/${reviewerId}/issue`)
}

/**
 * 【业务功能】：吊销或废止违规审核员的专业执业资格证书
 * 【对应前端页面】：CertificateManage.vue (/admin/certificates)
 * 【界面触发操作】：在审核员资格列表操作列点击【吊销资质】并二次确认
 * 【后端对应端点】：AdminController -> DELETE /admin/reviewer-qualification/{id}
 */
export function revokeReviewerQualification(reviewerId: number): Promise<void> {
  return request.delete(`/admin/reviewer-qualification/${reviewerId}`)
}

/**
 * 【业务功能】：获取管理员全局运行仪表板核心宏观指标数据
 * 【对应前端页面】：DataStatistics.vue (/admin/data/statistics)
 * 【界面触发操作】：进入数据统计大屏自动拉取全网概览数据
 * 【后端对应端点】：AdminController -> GET /admin/dashboard
 */
export function getDashboard(): Promise<AdminDashboardResponse> {
  return request.get('/admin/dashboard')
}

/**
 * 【业务功能】：查询平台底层系统配置参数（区块链网络通道、上传体积限制、会话有效期等）
 * 【对应前端页面】：SystemConfig.vue (/admin/system/config)
 * 【界面触发操作】：进入系统配置页面展示当前只读参数列表
 * 【后端对应端点】：AdminController -> GET /admin/config
 */
export function getConfig(): Promise<AdminConfigResponse> {
  return request.get('/admin/config')
}

/**
 * 【业务功能】：查询系统权限清单与角色访问控制规则定义
 * 【对应前端页面】：SystemConfig.vue (/admin/system/config)
 * 【界面触发操作】：系统配置页面切换至“权限矩阵与安全策略”查看
 * 【后端对应端点】：AdminController -> GET /admin/permissions
 */
export function getPermissions(): Promise<AdminPermissionResponse[]> {
  return request.get('/admin/permissions')
}
