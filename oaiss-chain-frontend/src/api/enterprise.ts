/**
 * ==============================================================================
 * 【API 模块】：控排企业主体档案与法定配额资产 (Enterprise API)
 * 【对应后端】：com.oaiss.chain.controller.EnterpriseController (/enterprise)
 * 【主要服务页面】：
 *   - 企业信息维护: views/enterprise/EnterpriseInfo.vue (/enterprise/info)
 *   - 企业综合看板: views/enterprise/CompanyDashboard.vue (/enterprise/company/dashboard)
 *   - 个人中心准入状态: views/enterprise/UserProfile.vue (/enterprise/user/profile)
 *   - 双向拍卖与P2P交易: views/enterprise/TradingMarket.vue, TradingP2P.vue
 * ==============================================================================
 */
import request from './request'
import type { EnterpriseAdmissionResponse } from '../types/admin'
import type { EnterpriseQuotaResponse, EnterpriseResponse } from '../types/user'

/**
 * 【业务功能】：获取当前登录企业的主体工商档案与资质信息
 * 【对应前端页面】：EnterpriseInfo.vue (/enterprise/info), CompanyDashboard.vue
 * 【界面触发操作】：进入企业信息页面或综合大屏自动加载企业名称、行业、统一社会信用代码等
 * 【后端对应端点】：EnterpriseController -> GET /enterprise/info
 */
export function getEnterpriseInfo(): Promise<EnterpriseResponse> {
  return request.get('/enterprise/info')
}

/**
 * 【业务功能】：查询当前企业的法定碳配额资产账户（总配额、可用配额、已履约配额与使用率）
 * 【对应前端页面】：CompanyDashboard.vue, EnterpriseInfo.vue
 * 【界面触发操作】：综合看板上方核心资产卡片与配额使用率环形进度条渲染
 * 【后端对应端点】：EnterpriseController -> GET /enterprise/quota
 */
export function getQuotaInfo(): Promise<EnterpriseQuotaResponse> {
  return request.get('/enterprise/quota')
}

/**
 * 【业务功能】：修改企业法定联系人与应急联系电话
 * 【对应前端页面】：EnterpriseInfo.vue (/enterprise/info)
 * 【界面触发操作】：在“联系人信息”表单修改后，点击【保存联系人】按钮
 * 【后端对应端点】：EnterpriseController -> PUT /enterprise/contact
 */
export function updateContact(data: { contactPerson: string; contactPhone: string }): Promise<void> {
  if (!data?.contactPerson) return Promise.reject(new Error('Contact person is required'))
  if (!data?.contactPhone) return Promise.reject(new Error('Contact phone is required'))
  return request.put('/enterprise/contact', data)
}

/**
 * 【业务功能】：查询当前企业自身的市场准入证书列表与生效资质
 * 【对应前端页面】：UserProfile.vue (/enterprise/user/profile), CompanyDashboard.vue
 * 【界面触发操作】：个人中心/企业主页展示准入证书卡片及审核状态（有效/吊销）
 * 【后端对应端点】：EnterpriseController -> GET /enterprise/admission/my
 */
export function getMyEnterpriseAdmission(): Promise<EnterpriseAdmissionResponse[]> {
  return request.get('/enterprise/admission/my')
}

/**
 * 【业务功能】：根据企业ID查询指定公开企业的主体档案信息
 * 【对应前端页面】：TradingP2P.vue, OrdersManage.vue, AuditList.vue
 * 【界面触发操作】：在P2P转让中选择对手方，或查看交易对手/报告申报企业基本资料
 * 【后端对应端点】：EnterpriseController -> GET /enterprise/{enterpriseId}
 */
export function getEnterpriseById(enterpriseId: number): Promise<EnterpriseResponse> {
  if (!enterpriseId) return Promise.reject(new Error('Enterprise ID is required'))
  return request.get(`/enterprise/${enterpriseId}`)
}

/** 交易对手搜索结果项（与后端 EnterpriseSearchResponse 对应） */
export interface EnterpriseSearchItem {
  enterpriseId: number
  /** P2P 建单以 userId 定位买方；**不可展示给用户** */
  userId: number
  enterpriseName: string
  creditCode: string
  creditScore: number
}

/**
 * 【业务功能】：按企业名称或统一社会信用代码远程搜索交易对手
 * 【对应后端端点】：EnterpriseController -> GET /enterprise/search
 * 【对应前端页面】：views/enterprise/TradingP2P.vue（P2P 建单的对手方选择器）
 *                   views/admin/CertificateManage.vue（管理员选企业）
 *
 * 【替代了什么】：原实现是 getScoreRanking(前50) + 逐个 getEnterpriseById，
 * 最多 51 次请求，企业超过 50 家时静默截断，且下拉文案硬拼了内部 userId。
 */
export function searchEnterprises(
  keyword: string,
  pageNum = 1,
  pageSize = 20,
): Promise<{ items: EnterpriseSearchItem[]; total: number }> {
  return request.get('/enterprise/search', {
    params: { keyword, page: pageNum, size: pageSize },
  })
}
