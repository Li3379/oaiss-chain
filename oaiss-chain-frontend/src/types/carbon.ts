/** 碳报告请求 */
export interface CarbonReportRequest {
  accountingPeriod: string
  title: string
  reportType: number
  emissionData: string
  calculationMethod?: string
  attachments?: string
  signatureData?: string
  // ===== 组件1：配额发放依据 =====
  /** 核算年份（唯一约束建在 periodYear + periodQuarter 上，不依赖 accountingPeriod 字符串） */
  periodYear?: number
  /** 核算季度 1-4；年度报告不传 */
  periodQuarter?: number
  /** 发电量（MWh）——配额公式的乘数因子 */
  outputQuantity?: number
  /** 发电量单位，固定 MWh */
  outputUnit?: string
  /** 机组类别 I/II/III/IV，决定查哪一行基准值 */
  unitType?: string
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
  /** 链上存证标记：HISTORICAL_MOCK 表示该哈希仅存于本地库，链上并不存在 */
  chainMark?: string | null
  chainMarkReason?: string | null
  chainMarkedAt?: string | null
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
