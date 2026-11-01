/**
 * ==============================================================================
 * 【API 模块】：监管线索 (Regulatory Finding API)
 * 【对应后端】：com.oaiss.chain.controller.RegulatoryFindingController (/regulatory)
 * 【主要服务页面】：
 *   - 监管面板线索区: views/third-party/Monitor.vue (/third-party/monitor)
 * ==============================================================================
 */
import request from './request'
import type { PageResponse } from '../types/api'

/** 严重等级（与后端 RegulatoryFinding.SEVERITY_* 一致） */
export const FINDING_SEVERITY = {
  LOW: 'LOW',
  MEDIUM: 'MEDIUM',
  HIGH: 'HIGH',
  CRITICAL: 'CRITICAL',
} as const

/** 规则代码（与后端 RegulatoryFinding.RULE_* 一致） */
export const FINDING_RULES = {
  REPEATED_REJECTION: 'REPEATED_REJECTION',
  QUOTA_OVERDRAFT: 'QUOTA_OVERDRAFT',
  CREDIT_PLUNGE: 'CREDIT_PLUNGE',
  WASH_TRADING_BLOCKED: 'WASH_TRADING_BLOCKED',
} as const

export interface RegulatoryFindingRecord {
  id: number
  enterpriseId: number
  ruleCode: string
  severity: string
  /** 触发依据（含实际数值，如「近 30 日成交被驳回 5 次」） */
  evidence?: string | null
  resolved: boolean
  resolvedBy?: number | null
  resolvedAt?: string | null
  resolveNote?: string | null
  createdAt?: string
}

export interface FindingQuery {
  pageNum: number
  pageSize: number
}

/**
 * 【业务功能】：分页查询未处置线索
 * 【对应后端端点】：GET /regulatory/findings
 */
export function listFindings(query: FindingQuery): Promise<PageResponse<RegulatoryFindingRecord>> {
  return request.get('/regulatory/findings', {
    params: { page: query.pageNum, size: query.pageSize },
  })
}

/**
 * 【业务功能】：标记线索已处置。
 *
 * 注意：这不改变任何企业或交易状态（不冻结、不扣分、不下市），
 * 只是给系统产出的一条发现打上「监管已处理」的印记。
 * 【对应后端端点】：PUT /regulatory/findings/{id}/resolve
 */
export function resolveFinding(findingId: number, note?: string): Promise<RegulatoryFindingRecord> {
  if (!findingId) return Promise.reject(new Error('线索ID不能为空'))
  return request.put(`/regulatory/findings/${findingId}/resolve`, { note: note ?? null })
}
