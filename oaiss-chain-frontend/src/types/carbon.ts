/** 碳报告请求 */
export interface CarbonReportRequest {
  accountingPeriod: string
  title: string
  reportType: number
  emissionData: string
  calculationMethod?: string
  attachments?: string
  signatureData?: string
}

/** 碳报告响应 */
export interface CarbonReportResponse {
  id: number
  reportNo: string
  enterpriseId: number
  enterpriseName: string
  accountingPeriod: string
  title: string
  reportType: number
  emissionData: string
  totalEmission: number
  scope1Emission: number
  scope2Emission: number
  scope3Emission: number
  calculationMethod: string
  status: number
  statusText: string
  reviewerId: number
  reviewerName: string
  reviewComment: string
  reviewedAt: string
  signatureData: string
  /** 签名验证结果：true=有效 false=无效(可能被篡改) null=无签名或未验证 */
  signatureValid?: boolean | null
  blockchainTxHash: string
  onChainAt: string
  attachments: string
  createdAt: string
  updatedAt: string
}

/** 审核请求 */
export interface ReviewRequest {
  reportId: number
  reviewResult: number
  reviewComment: string
}

export interface BatchCertifyRequest {
  reportIds: number[]
  reviewResult: number
  reviewComment?: string
}

export interface BatchCertifyResponse {
  totalCount: number
  successCount: number
  failureCount: number
  successReportIds: number[]
  errorMessages: string[]
}

/** 碳报告实体 (ThirdParty接口返回) */
export interface CarbonReport {
  id: number
  reportNo: string
  enterpriseId: number
  submitterId: number
  accountingPeriod: string
  title: string
  reportType: number
  emissionData: string
  totalEmission: number
  scope1Emission: number
  scope2Emission: number
  scope3Emission: number
  calculationMethod: string
  status: number
  reviewerId: number
  reviewComment: string
  reviewedAt: string
  signatureData: string
  blockchainTxHash: string
  onChainAt: string
  attachments: string
  createdAt: string
  updatedAt: string
  deleted: boolean
}
