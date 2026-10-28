/** 用户类型 */
export const UserTypeEnum = {
  ENTERPRISE: 1,
  REVIEWER: 2,
  THIRD_PARTY: 3,
  ADMIN: 4,
} as const
export type UserType = (typeof UserTypeEnum)[keyof typeof UserTypeEnum]

/** 报告状态 */
export const ReportStatusEnum = {
  DRAFT: 0,
  SUBMITTED: 1,
  UNDER_REVIEW: 2,
  APPROVED: 3,
  REJECTED: 4,
  ON_CHAIN: 5,
} as const
export type ReportStatus = (typeof ReportStatusEnum)[keyof typeof ReportStatusEnum]

/** 交易状态 */
export const TradeStatusEnum = {
  PENDING: 0,
  PROCESSING: 1,
  COMPLETED: 2,
  CANCELLED: 3,
  FAILED: 4,
} as const
export type TradeStatus = (typeof TradeStatusEnum)[keyof typeof TradeStatusEnum]

/** 交易类型 */
export const TradeTypeEnum = {
  AUCTION: 1,
  P2P: 2,
  ALLOCATION: 3,
} as const
export type TradeType = (typeof TradeTypeEnum)[keyof typeof TradeTypeEnum]

/** 拍卖订单状态 */
export const AuctionOrderStatusEnum = {
  PENDING: 0,
  PARTIALLY_MATCHED: 1,
  FULLY_MATCHED: 2,
  CANCELLED: 3,
} as const
export type AuctionOrderStatus = (typeof AuctionOrderStatusEnum)[keyof typeof AuctionOrderStatusEnum]

/** 撮合状态 */
export const MatchingStatusEnum = {
  PENDING_SETTLEMENT: 0,
  SETTLED: 1,
  FAILED: 2,
} as const
export type MatchingStatus = (typeof MatchingStatusEnum)[keyof typeof MatchingStatusEnum]

/** 信用事件类型 */
export const CreditEventTypeEnum = {
  DATA_FALSIFICATION: 1,
  LATE_SUBMISSION: 2,
  MINOR_VIOLATION: 3,
  MAJOR_VIOLATION: 4,
  BONUS_GOOD_BEHAVIOR: 5,
} as const
export type CreditEventType = (typeof CreditEventTypeEnum)[keyof typeof CreditEventTypeEnum]

/** 信用等级 */
export const CreditLevelEnum = {
  EXCELLENT: 'EXCELLENT',
  GOOD: 'GOOD',
  WARNING: 'WARNING',
  DANGER: 'DANGER',
  FROZEN: 'FROZEN',
} as const
export type CreditLevel = (typeof CreditLevelEnum)[keyof typeof CreditLevelEnum]

/** 碳中和项目类型 */
export const CarbonNeutralProjectTypeEnum = {
  CARBON_SINK: 1,
  CCUS: 2,
  RENEWABLE: 3,
  ENERGY_SAVING: 4,
  OTHER: 5,
} as const
export type CarbonNeutralProjectType = (typeof CarbonNeutralProjectTypeEnum)[keyof typeof CarbonNeutralProjectTypeEnum]

/** 碳中和项目状态 */
export const CarbonNeutralStatusEnum = {
  PREPARATION: 0,
  PENDING_REVIEW: 1,
  APPROVED: 2,
  IMPLEMENTING: 3,
  COMPLETED: 4,
  TERMINATED: 5,
  REVIEW_REJECTED: 6,
} as const
export type CarbonNeutralStatus = (typeof CarbonNeutralStatusEnum)[keyof typeof CarbonNeutralStatusEnum]

/** 碳币交易类型 */
export const CarbonCoinTxTypeEnum = {
  RECHARGE: 1,
  BUY_QUOTA: 2,
  SELL_QUOTA: 3,
  TRANSFER: 4,
} as const
export type CarbonCoinTxType = (typeof CarbonCoinTxTypeEnum)[keyof typeof CarbonCoinTxTypeEnum]

/** 报告类型 */
export const ReportTypeEnum = {
  QUARTERLY: 1,
  ANNUAL: 2,
} as const
export type ReportType = (typeof ReportTypeEnum)[keyof typeof ReportTypeEnum]

/** 认证状态 */
export const CertStatusEnum = {
  UNCERTIFIED: 0,
  CERTIFYING: 1,
  CERTIFIED: 2,
} as const
export type CertStatus = (typeof CertStatusEnum)[keyof typeof CertStatusEnum]

/** 核证状态 */
export const VerificationStatusEnum = {
  UNVERIFIED: 0,
  VERIFYING: 1,
  VERIFIED: 2,
  VERIFICATION_FAILED: 3,
} as const
export type VerificationStatus = (typeof VerificationStatusEnum)[keyof typeof VerificationStatusEnum]
