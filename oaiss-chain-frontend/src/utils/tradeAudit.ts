/**
 * 成交审核相关的前端纯函数。
 *
 * 抽成独立模块而不是留在 SFC 里，原因：
 * 1) 风险理由的解析规则是**数据契约**（后端存 JSON 数组字符串），
 *    值得单独测试且应能脱离组件渲染被断言
 * 2) 历史数据里该字段可能为 null 或非 JSON 脏值，容错分支必须可测
 */

/**
 * 解析后端存储的风险理由。
 *
 * 输入是 `trade_audit.risk_reasons` 的原文（JSON 数组字符串，
 * 如 `["LARGE_AMOUNT(1500>=1000)"]`）。
 *
 * 容错策略：解析失败时**退回原文**而不是丢弃——审核员看不到理由就失去了
 * 判断依据，宁可用脏文本也不能让工单变成无解释的空白。
 */
export function parseRiskReasons(raw: string | null | undefined): string[] {
  if (!raw) return []

  try {
    const parsed: unknown = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item))
    }
    // JSON 合法但不是数组（历史脏数据）：按原文展示
    return [raw]
  } catch {
    return [raw]
  }
}

/** 审核态：0 无需审核 / 1 待审核 / 2 已通过 / 3 已驳回 */
export const AUDIT_STATUS = {
  NOT_REQUIRED: 0,
  PENDING: 1,
  APPROVED: 2,
  REJECTED: 3,
} as const

/** 驳回理由代码（与后端 TradeAudit.REASON_* 一致） */
export const REJECT_REASONS = [
  'LARGE_AMOUNT',
  'PRICE_DEVIATION',
  'LOW_CREDIT',
  'WASH_TRADING',
  'OTHER',
] as const

export type RejectReason = (typeof REJECT_REASONS)[number]
