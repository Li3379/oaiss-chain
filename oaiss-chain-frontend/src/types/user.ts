/** User info returned by profile and user detail endpoints. */
export interface UserInfoResponse {
  userId: number
  username: string
  realName: string
  phone: string
  email: string
  avatar: string
  company: string
  address: string
  userType: number
  userTypeDesc: string
  status: number
  lastLoginAt: string
  lastLoginIp: string
  createdAt: string
  totalStaySeconds?: number
}

export interface StayTimeStats {
  totalSeconds: number
  hours: number
  minutes: number
  seconds: number
  formatted: string
}

/** Request payload for updating the current user profile. */
export interface UserProfileUpdateRequest {
  realName?: string
  phone?: string
  email?: string
  avatar?: string
  company?: string
  address?: string
}

/** User entity returned by admin endpoints. */
export interface User {
  id: number
  username: string
  phone: string
  email: string
  realName: string
  userType: number
  status: number
  allowedIps: string
  lastLoginAt: string
  lastLoginIp: string
  avatar: string
  createdAt: string
  updatedAt: string
  deleted: boolean
}

/** Enterprise profile returned by enterprise endpoints. */
export interface EnterpriseResponse {
  id: number
  userId: number
  enterpriseName: string
  creditCode: string
  address: string
  contactPerson: string
  contactPhone: string
  industry: string
  scale: string
  carbonQuota: number | string
  carbonUsed: number | string
  carbonTradable: number | string
  licenseUrl: string
  certStatus: number
  createdAt: string
  updatedAt: string
}

/** Enterprise quota summary returned by `/enterprise/quota`. */
export interface EnterpriseQuotaResponse {
  totalQuota: number | string
  usedQuota: number | string
  tradableQuota: number | string
  enterpriseName: string
  remainingQuota: number | string
  usageRate: number | string
}
