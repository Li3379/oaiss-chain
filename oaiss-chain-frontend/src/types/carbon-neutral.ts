/** 碳中和项目请求 */
export interface CarbonNeutralProjectRequest {
  projectName: string
  projectType: number
  description?: string
  location?: string
  expectedReduction?: number
  investmentAmount?: number
  startDate?: string
  endDate?: string
  methodology?: string
  accountingPeriod?: number
  applicationData?: string
  attachments?: string
}

/** 碳中和项目响应 */
export interface CarbonNeutralProjectResponse {
  id: number
  projectNo: string
  projectName: string
  projectType: number
  projectTypeName: string
  ownerId: number
  ownerName: string
  description: string
  location: string
  expectedReduction: number
  actualReduction: number
  investmentAmount: number
  startDate: string
  endDate: string
  status: number
  statusText: string
  certStatus: number
  certStatusText: string
  certOrg: string
  certDate: string
  certNo: string
  methodology: string
  accountingPeriod: number
  issuedCredits: number
  usedCredits: number
  availableCredits: number
  applicationData: string
  verificationReport: string
  attachments: string
  reviewComment: string
  reviewerId: number
  reviewerName: string
  reviewedAt: string
  monitoringData: string
  lastMonitoringDate: string
  verifierId: number
  verifierName: string
  verificationStatus: number
  verificationStatusText: string
  createdAt: string
  updatedAt: string
}

/** 项目核证请求 */
export interface ProjectVerificationRequest {
  projectId: number
  verifiedReduction: number
  verificationReport?: string
  monitoringData?: string
  remark?: string
}
