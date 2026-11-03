/**
 * ==============================================================================
 * 【API 模块】：碳中和项目培育、核证与碳信用抵消 (Carbon Neutral Projects API)
 * 【对应后端】：com.oaiss.chain.controller.CarbonNeutralProjectController (/carbon-neutral)
 * 【主要服务页面】：
 *   - 碳中和项目列表: views/enterprise/CarbonNeutral.vue (/enterprise/carbon-neutral/projects)
 *   - 碳中和项目详情: views/enterprise/CarbonNeutralDetail.vue (/enterprise/carbon-neutral/projects/:id)
 *   - 核查员项目核证: views/auditor/ProjectReview.vue (/auditor/project/review)
 * ==============================================================================
 */
import request from './request'
import type {
  CarbonNeutralProjectRequest,
  CarbonNeutralProjectResponse,
  ProjectVerificationRequest,
  PageRequest,
  PagedItems,
} from '../types'

/** 企业申请核证请求体：后端 SubmitVerificationRequest 只接受 verifierId（项目ID 走路径） */
export interface SubmitVerificationRequest {
  verifierId: number
}

/** 企业申请认证请求体：后端 ApplyCertificationRequest 要求 certification organization（@NotBlank） */
export interface ApplyCertificationRequest {
  certOrg: string
}

/** 完成认证请求体：后端 CompleteCertificationRequest 要求证书编号（@NotBlank） */
export interface CompleteCertificationRequest {
  certNo: string
}

/**
 * 【业务功能】：多维度检索公开的碳中和项目列表（按项目类型、状态、地区过滤）
 * 【对应前端页面】：CarbonNeutral.vue (/enterprise/carbon-neutral/projects)
 * 【界面触发操作】：项目列表页按类型、阶段筛选与分页展示
 * 【后端对应端点】：CarbonNeutralProjectController -> GET /carbon-neutral/search
 */
export function getProjects(params?: PageRequest): Promise<PagedItems<CarbonNeutralProjectResponse>> {
  return request.get('/carbon-neutral/search', { params })
}

/**
 * 【业务功能】：获取单个碳中和项目的全景详情（基本方案、监测数据、核查历史与碳信用签发）
 * 【对应前端页面】：CarbonNeutralDetail.vue (/enterprise/carbon-neutral/projects/:id)
 * 【界面触发操作】：在项目列表中点击【查看项目详情】进入全景看板
 * 【后端对应端点】：CarbonNeutralProjectController -> GET /carbon-neutral/{id}
 */
export function getProject(projectId: number): Promise<CarbonNeutralProjectResponse> {
  return request.get(`/carbon-neutral/${projectId}`)
}

/**
 * 【业务功能】：企业发起申报新的碳中和/林业碳汇/CCUS减排项目（草稿立项）
 * 【对应前端页面】：CarbonNeutral.vue (/enterprise/carbon-neutral/projects)
 * 【界面触发操作】：点击【新建碳中和项目】按钮，填报项目名称、预期减排量、建设周期等并提交
 * 【后端对应端点】：CarbonNeutralProjectController -> POST /carbon-neutral
 */
export function createProject(data: CarbonNeutralProjectRequest): Promise<CarbonNeutralProjectResponse> {
  return request.post('/carbon-neutral', data)
}

/**
 * 【业务功能】：更新修改草稿阶段的项目设计方案资料
 * 【对应前端页面】：CarbonNeutralDetail.vue
 * 【界面触发操作】：在项目详情页点击【编辑项目信息】
 * 【后端对应端点】：CarbonNeutralProjectController -> PUT /carbon-neutral/{id}
 */
export function updateProject(projectId: number, data: Partial<CarbonNeutralProjectRequest>): Promise<CarbonNeutralProjectResponse> {
  return request.put(`/carbon-neutral/${projectId}`, data)
}

/**
 * 【业务功能】：将碳中和项目立项方案正式提交至专家评审
 * 【对应前端页面】：CarbonNeutral.vue, CarbonNeutralDetail.vue
 * 【界面触发操作】：在项目卡片或详情页点击【提交立项审批】
 * 【后端对应端点】：CarbonNeutralProjectController -> POST /carbon-neutral/{id}/submit
 */
export function submitProject(projectId: number): Promise<void> {
  return request.post(`/carbon-neutral/${projectId}/submit`)
}

/**
 * 【业务功能】：通过立项审批后，正式启动项目工程落地实施
 * 【对应前端页面】：CarbonNeutralDetail.vue
 * 【界面触发操作】：立项通过后，企业点击【启动项目实施】按钮
 * 【后端对应端点】：CarbonNeutralProjectController -> POST /carbon-neutral/{id}/start
 */
export function startProject(projectId: number): Promise<void> {
  return request.post(`/carbon-neutral/${projectId}/start`)
}





/**
 * 【业务功能】：企业用户查询自身申报的所有碳中和项目列表
 * 【对应前端页面】：CarbonNeutral.vue (/enterprise/carbon-neutral/projects)
 * 【界面触发操作】：进入页面加载“我申报的项目”列表
 * 【后端对应端点】：CarbonNeutralProjectController -> GET /carbon-neutral/my
 */
export function getMyProjects(params?: PageRequest): Promise<PagedItems<CarbonNeutralProjectResponse>> {
  return request.get('/carbon-neutral/my', { params })
}

/**
 * 【业务功能】：专家/监管方审核碳中和项目立项方案（审批通过或驳回）
 * 【对应前端页面】：ProjectReview.vue (/auditor/project/review)
 * 【界面触发操作】：审核员在待审项目抽屉中点击【通过立项】或【驳回修改】
 * 【后端对应端点】：CarbonNeutralProjectController -> POST /carbon-neutral/{id}/review
 */
export function reviewProject(projectId: number, data: { approved: boolean; comment: string }): Promise<void> {
  if (!projectId) return Promise.reject(new Error('项目ID不能为空'))
  return request.post(`/carbon-neutral/${projectId}/review`, data)
}


/**
 * 【业务功能】：企业使用/注销项目签发的碳信用抵消自身的超额碳排放
 * 【对应前端页面】：CarbonNeutralDetail.vue
 * 【界面触发操作】：在“碳信用抵消”区块输入拟核销抵扣的碳信用额度并确认抵销
 * 【后端对应端点】：CarbonNeutralProjectController -> POST /carbon-neutral/{id}/use-credits
 */
export function useCredits(projectId: number, data: { amount: number }): Promise<void> {
  if (!projectId) return Promise.reject(new Error('项目ID不能为空'))
  return request.post(`/carbon-neutral/${projectId}/use-credits`, data)
}


/**
 * 【业务功能】：查询待第三方核证机构处理的碳中和项目工单池
 * 【对应前端页面】：ProjectReview.vue (/auditor/project/review)
 * 【界面触发操作】：审核员进入“项目核证”页面加载待审任务
 * 【后端对应端点】：CarbonNeutralProjectController -> GET /carbon-neutral/pending-verification
 */
export function getPendingVerification(params?: PageRequest): Promise<PagedItems<CarbonNeutralProjectResponse>> {
  return request.get('/carbon-neutral/pending-verification', { params })
}
