/**
 * ==============================================================================
 * 【API 模块】：成交审核 (Trade Audit API)
 * 【对应后端】：com.oaiss.chain.controller.TradeAuditController (/trade-audit)
 * 【主要服务页面】：
 *   - 审核员成交审核工作台: views/auditor/TradeAudit.vue (/auditor/trade-audit)
 * ==============================================================================
 */
import request from './request'
import type { PageResponse } from '../types/api'
// 审核态与驳回理由属于跨层契约（后端枚举镜像），定义在 utils 供页面/测试共用
import { AUDIT_STATUS, REJECT_REASONS } from '../utils/tradeAudit'
import type { RejectReason } from '../utils/tradeAudit'

export { AUDIT_STATUS, REJECT_REASONS }
export type { RejectReason }

/** 成交审核工单 */
export interface TradeAuditRecord {
  id: number
  tradeId: number
  auditStatus: number
  /** 命中的风险理由（JSON 数组字符串，如 ["LARGE_AMOUNT(1500>=1000)"]） */
  riskReasons?: string | null
  decisionReason?: string | null
  auditRemark?: string | null
  auditorId?: number | null
  auditedAt?: string | null
  fallbackDecided?: boolean
  createdAt?: string
}

export interface TradeAuditQuery {
  pageNum: number
  pageSize: number
  /** 不传默认待审核（1） */
  status?: number
}

/**
 * 【业务功能】：分页查询成交审核工单
 * 【对应后端端点】：GET /trade-audit
 */
export function listPendingAudits(
  query: TradeAuditQuery,
): Promise<PageResponse<TradeAuditRecord>> {
  const params: Record<string, number> = {
    page: query.pageNum,
    size: query.pageSize,
  }
  if (query.status !== undefined && query.status !== null) {
    params.status = query.status
  }
  return request.get('/trade-audit', { params })
}

/**
 * 【业务功能】：审核通过——后端在此刻才真正结算额度与碳币并触发上链
 * 【对应后端端点】：POST /trade-audit/{tradeId}/approve
 */
export function approveTrade(tradeId: number, remark: string): Promise<void> {
  // 前置拦截：后端同样要求非空，这里提前给出更快的反馈
  if (!tradeId) return Promise.reject(new Error('交易ID不能为空'))
  if (!remark || !remark.trim()) return Promise.reject(new Error('审核意见不能为空'))
  return request.post(`/trade-audit/${tradeId}/approve`, { remark: remark.trim() })
}

/**
 * 【业务功能】：审核驳回——解冻额度与碳币、扣双方信誉分、交易置已取消
 * 【对应后端端点】：POST /trade-audit/{tradeId}/reject
 */
export function rejectTrade(
  tradeId: number,
  remark: string,
  reason: RejectReason = 'OTHER',
): Promise<void> {
  if (!tradeId) return Promise.reject(new Error('交易ID不能为空'))
  if (!remark || !remark.trim()) return Promise.reject(new Error('审核意见不能为空'))
  return request.post(`/trade-audit/${tradeId}/reject`, {
    remark: remark.trim(),
    reason,
  })
}
