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
}

export interface ThirdPartyCarbonReportQuery {
  pageNum?: number
  pageSize?: number
  enterpriseId?: number
  keyword?: string
  status?: number
}

export type ThirdPartyCarbonReportPage = PageResponse<CarbonReport>
