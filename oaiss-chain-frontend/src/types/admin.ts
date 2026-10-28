import type { PagedItems } from './api'
import type { User } from './user'
import type { ReviewerQualificationResponse } from './reviewer'

export interface EnterpriseAdmissionResponse {
  id: number
  enterpriseId: number
  certificateNo: string
  issuedDate: string
  expiryDate: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface AdminStatisticsResponse {
  totalUsers: number
  enterpriseCount: number
  reviewerCount: number
  thirdPartyCount: number
}

export interface AdminDashboardResponse {
  totalUsers: number
  activeUsers: number
}

export interface AdminConfigResponse {
  systemName: string
  version: string
  maxUploadSize: string
  sessionTimeout: number
  enableCaptcha: boolean
  enableBlockChain: boolean
  [key: string]: string | number | boolean
}

export interface AdminPermissionResponse {
  id: number
  permissionName: string
  permissionCode: string
  description: string
  module: string
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export type AdminUserPage = PagedItems<User>
export type EnterpriseAdmissionPage = PagedItems<EnterpriseAdmissionResponse>
export type ReviewerQualificationPage = PagedItems<ReviewerQualificationResponse>
