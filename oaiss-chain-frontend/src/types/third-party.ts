import type { CarbonReport } from './carbon'
import type { PageResponse } from './api'

export interface ThirdPartyOrg {
  id: number
  userId: number
  orgName: string
  orgCode: string
  orgType: number
  supervisionScope: string
  contactPerson: string
  contactPhone: string
  address: string
  accessLevel: number
  status: number
  createdAt: string
  updatedAt: string
  deleted: boolean
}

export interface ThirdPartyStatistics {
  orgName?: string
  accessLevel?: number
  totalReports: number
  pendingReports: number
  approvedReports: number
  rejectedReports: number
  /** 全市场交易总笔数（监管穿透统计） */
  totalTrades?: number
  /** 全市场累计成交总量（吨 CO2 当量） */
  totalTradeQuantity?: number
  /** 全市场累计成交总额（元） */
  totalTradeAmount?: number
}

export interface ThirdPartyCarbonReportQuery {
  pageNum?: number
  pageSize?: number
  enterpriseId?: number
  keyword?: string
  status?: number
}

export type ThirdPartyCarbonReportPage = PageResponse<CarbonReport>
