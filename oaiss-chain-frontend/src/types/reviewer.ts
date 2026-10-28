export interface ReviewerInfoResponse {
  id: number
  userId: number
  qualificationNo: string
  level: number
  organization: string
  reviewableIndustries: string
  completedReviews: number
  status: number
  name?: string
}

export interface ReviewerStatisticsResponse {
  completedReviews?: number
  totalReviews?: number
  passedCount?: number
  approvedCount?: number
  rejectedCount?: number
  approvalRate?: string
  level?: number
  organization?: string
  pendingCount?: number
}

export interface ReviewerQualificationResponse {
  id: number
  reviewerId: number
  qualificationType: string
  certificateNo: string
  issuingAuthority: string
  issuedDate: string
  expiryDate: string
  status: number
  createdAt: string
  updatedAt: string
}
