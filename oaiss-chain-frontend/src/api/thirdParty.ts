/**
 * ==============================================================================
 * 【API 模块】：第三方监管部门穿透式监控与监管工作台 (Third Party API)
 * 【对应后端】：com.oaiss.chain.controller.ThirdPartyController (/third-party)
 * 【主要服务页面】：
 *   - 监管面板: views/third-party/Monitor.vue (/third-party/monitor)
 * ==============================================================================
 */
import request from './request'
import type {
  ThirdPartyCarbonReportPage,
  ThirdPartyCarbonReportQuery,
  ThirdPartyOrg,
  ThirdPartyStatistics,
} from '../types'

/**
 * 【业务功能】：监管方视角穿透式查询辖区内企业碳核算报告列表（支持按企业ID、状态、时间范围多维检索）
 * 【对应前端页面】：Monitor.vue (/third-party/monitor)
 * 【界面触发操作】：监管看板下方“全网企业排碳报告全量审计表”分页加载与检索
 * 【后端对应端点】：ThirdPartyController -> GET /third-party/carbon-reports
 */
export function getCarbonReports(params?: ThirdPartyCarbonReportQuery): Promise<ThirdPartyCarbonReportPage> {
  return request.get('/third-party/carbon-reports', { params })
}

/**
 * 【业务功能】：监管视角宏观业务统计数据（辖区已核算企业数、审核合规达标率、累计排碳监控总量）
 * 【对应前端页面】：Monitor.vue (/third-party/monitor)
 * 【界面触发操作】：监管大盘顶部核心宏观统计卡片（合规率、监测企业数、总排量等）
 * 【后端对应端点】：ThirdPartyController -> GET /third-party/statistics
 */
export function getStatistics(): Promise<ThirdPartyStatistics> {
  return request.get('/third-party/statistics')
}

/**
 * 【业务功能】：获取当前第三方认证/监管机构的主体基本档案与资质信息
 * 【对应前端页面】：Monitor.vue (/third-party/monitor)
 * 【界面触发操作】：监管看板“机构档案”卡片展示机构全称、统一代码与联系方式
 * 【后端对应端点】：ThirdPartyController -> GET /third-party/org-info
 */
export function getOrgInfo(): Promise<ThirdPartyOrg> {
  return request.get('/third-party/org-info')
}

/**
 * 【业务功能】：修改第三方监管机构的联系人与应急联络电话
 * 【对应前端页面】：Monitor.vue (/third-party/monitor)
 * 【界面触发操作】：在“机构联系人”表单修改后点击【保存联系人】
 * 【后端对应端点】：ThirdPartyController -> PUT /third-party/contact
 */
export function updateContact(data: { contactPerson: string; contactPhone: string }): Promise<void> {
  if (!data?.contactPerson) return Promise.reject(new Error('Contact person is required'))
  if (!data?.contactPhone) return Promise.reject(new Error('Contact phone is required'))
  return request.put('/third-party/contact', null, { params: data })
}
