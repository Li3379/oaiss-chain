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
  PageResponse,
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
 * 【界面触发操作】：在企业准入列表操作列点击【吊销证书】，确认弹窗中填写吊销原因（可选）
 * 【后端对应端点】：AdminController -> DELETE /admin/enterprise-admission/{id}
 */
export function revokeEnterpriseAdmission(enterpriseId: number, reason?: string): Promise<void> {
  return request.delete(`/admin/enterprise-admission/${enterpriseId}`, {
    params: reason ? { reason } : undefined,
  })
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
 * 【界面触发操作】：在审核员资格列表操作列点击【吊销资质】，确认弹窗中填写吊销原因（可选）
 * 【后端对应端点】：AdminController -> DELETE /admin/reviewer-qualification/{id}
 */
export function revokeReviewerQualification(reviewerId: number, reason?: string): Promise<void> {
  return request.delete(`/admin/reviewer-qualification/${reviewerId}`, {
    params: reason ? { reason } : undefined,
  })
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

/**
 * 【业务功能】：管理员创建用户账号（审核员等无法自助注册角色的唯一创建通道）
 * 【对应前端页面】：SystemUsers.vue (/admin/system/users)
 * 【界面触发操作】：点击【新增用户】按钮，在弹窗表单录入账号信息提交
 * 【后端对应端点】：AdminController -> POST /admin/users
 */
export function createUser(data: {
  username: string
  password: string
  userType: number
  realName?: string
  phone?: string
  email?: string
}): Promise<{ id: number; username: string }> {
  return request.post('/admin/users', data)
}

/**
 * 【业务功能】：管理员删除用户（软删除=下线语义）
 * 【对应前端页面】：SystemUsers.vue (/admin/system/users)
 * 【界面触发操作】：在用户表格操作列点击【删除】按钮，二次确认后提交
 * 【后端对应端点】：AdminController -> DELETE /admin/users/{userId}
 */
export function deleteUser(userId: number): Promise<void> {
  return request.delete(`/admin/users/${userId}`)
}

/**
 * 链上对账结果：本地已标记上链的报告与链上快照的逐条比对。
 */
export interface ChainReconciliationDetail {
  reportId: number
  reportNo?: string
  dbTxHash?: string
  chainTxHash?: string
  dbTotalEmission?: string
  chainTotalEmission?: string
  /** 哈希是否真实存在于该报告的账本历史。null = 无法判定（未验证，不是「哈希为假」） */
  txHashVerified?: boolean | null
  status:
    | 'MATCHED'
    | 'MISSING_ON_CHAIN'
    | 'CONTENT_MISMATCH'
    | 'UNVERIFIABLE'
    | 'INSUFFICIENT_DATA'
    | 'HISTORICAL_V1'
    | 'HISTORICAL_MOCK'
    | string
  /** 内容比对中具体不符的字段（如 totalEmission / signature / txHash） */
  mismatchFields?: string[]
  reason?: string
}

export interface ChainReconciliationResult {
  /** 全库参与对账的报告总数（7 类计数与它同口径，由同一次遍历产出） */
  checkedGlobal: number
  /** 当前页条数（仅供前端标注「本页」） */
  checkedPage: number
  /** 哈希已验证且全字段一致 */
  matched: number
  /** 链上有快照但金额/参与字段不足，无法判定是否一致 */
  insufficientData: number
  /** 链上为 v1 结构（无金额字段），仅做哈希验证、不做内容比对 */
  historicalV1: number
  missingOnChain: number
  contentMismatch: number
  unverifiable: number
  /** 已标记为历史 Mock 欠账、不再重复告警的记录数 */
  historicalMock: number
  details: ChainReconciliationDetail[]
  total: number
}

/** 死信消息（超过重试上限仍未上链） */
export interface DeadLetterRow {
  id: number
  messageId: string
  aggregateType: string
  aggregateId: number
  topic: string
  payload?: string
  status: string
  retryCount: number
  nextRetryAt?: string
  errorMessage?: string
  createdAt?: string
}

/**
 * 【业务功能】：链上对账 — 比对本地已上链报告与链上快照
 * 【对应前端页面】：ChainOps.vue (/admin/blockchain)
 * 【界面触发操作】：链上管理页「对账」Tab 点击【重新对账】
 * 【后端对应端点】：AdminController -> GET /admin/blockchain/reconciliation
 */
export function getChainReconciliation(params?: PageRequest): Promise<ChainReconciliationResult> {
  return request.get('/admin/blockchain/reconciliation', { params })
}

/**
 * 【业务功能】：查询死信队列
 * 【对应前端页面】：ChainOps.vue (/admin/blockchain)
 * 【界面触发操作】：链上管理页「死信队列」Tab 加载表格
 * 【后端对应端点】：AdminController -> GET /admin/outbox/dead-letters
 */
export function getDeadLetters(params?: PageRequest): Promise<PageResponse<DeadLetterRow>> {
  return request.get('/admin/outbox/dead-letters', { params })
}

/**
 * 【业务功能】：重放一条死信（复位为 PENDING 交由调度器重投）
 * 【对应前端页面】：ChainOps.vue (/admin/blockchain)
 * 【界面触发操作】：死信列表操作列点击【重放】并二次确认
 * 【后端对应端点】：AdminController -> POST /admin/outbox/{id}/replay
 */
/**
 * 【业务功能】：标记历史 Mock 脏数据（tx_mock_* 哈希：本地记了上链、链上从未写入）
 * 【对应前端页面】：ChainOps.vue (/admin/blockchain)
 * 【界面触发操作】：对账 Tab 点击【标记历史 Mock 数据】并二次确认
 * 【后端对应端点】：AdminController -> POST /admin/blockchain/mark-historical-mock
 */
export function markHistoricalMockData(): Promise<{ marked: number }> {
  return request.post('/admin/blockchain/mark-historical-mock')
}

export function replayDeadLetter(id: number): Promise<void> {
  return request.post(`/admin/outbox/${id}/replay`)
}
