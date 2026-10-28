export interface ApiResponseEnvelope<T> {
  code: number
  message: string
  data: T
  meta: null
}

export function wrapSuccess<T>(data: T): ApiResponseEnvelope<T> {
  return { code: 200, message: 'ok', data, meta: null }
}

export function createCarbonReport(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    reportNo: 'CR-2026-001',
    enterpriseId: 1,
    enterpriseName: '绿色能源科技有限公司',
    accountingPeriod: '2026-Q1',
    title: '2026年第一季度碳排放报告',
    reportType: 1,
    emissionData: '{"scope1":5000,"scope2":3000,"scope3":2000}',
    totalEmission: 10000,
    scope1Emission: 5000,
    scope2Emission: 3000,
    scope3Emission: 2000,
    calculationMethod: '排放因子法',
    status: 0,
    statusText: '草稿',
    reviewerId: null,
    reviewerName: null,
    reviewComment: null,
    reviewedAt: null,
    signatureData: null,
    blockchainTxHash: null,
    onChainAt: null,
    attachments: null,
    createdAt: '2026-05-01 10:00:00',
    updatedAt: '2026-05-01 10:00:00',
    ...overrides,
  }
}

export function createAuctionOrder(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    orderNo: 'AO-2026-001',
    userId: 2,
    direction: 1,
    directionText: '买入',
    quantity: 1000,
    price: 50.0,
    matchedQuantity: 0,
    remainingQuantity: 1000,
    status: 0,
    statusText: '待撮合',
    settlementPrice: null,
    matchedAt: null,
    createdAt: '2026-05-01 10:00:00',
    ...overrides,
  }
}

export function createTrade(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    tradeNo: 'TR-2026-001',
    tradeType: 2,
    tradeTypeText: 'P2P交易',
    sellerId: 1,
    sellerName: '绿色能源科技有限公司',
    buyerId: 2,
    buyerName: '低碳制造股份有限公司',
    quantity: 500,
    unitPrice: 52.0,
    totalAmount: 26000,
    reportId: null,
    status: 0,
    statusText: '待确认',
    remark: null,
    blockchainTxHash: null,
    completedAt: null,
    createdAt: '2026-05-01 10:00:00',
    ...overrides,
  }
}

export function createCarbonNeutralProject(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    projectNo: 'CN-2026-001',
    projectName: '光伏发电碳中和项目',
    projectType: 3,
    projectTypeName: '可再生能源',
    ownerId: 1,
    ownerName: '绿色能源科技有限公司',
    description: '100MW光伏发电项目',
    location: '新疆维吾尔自治区',
    expectedReduction: 50000,
    actualReduction: 0,
    investmentAmount: 50000000,
    startDate: '2026-01-01',
    endDate: '2028-12-31',
    status: 0,
    statusText: '筹备中',
    certStatus: 0,
    certStatusText: '未认证',
    certOrg: null,
    certDate: null,
    certNo: null,
    methodology: 'CDM',
    accountingPeriod: 12,
    issuedCredits: 0,
    usedCredits: 0,
    availableCredits: 0,
    applicationData: null,
    verificationReport: null,
    attachments: null,
    reviewComment: null,
    reviewerId: null,
    reviewerName: null,
    reviewedAt: null,
    monitoringData: null,
    lastMonitoringDate: null,
    verifierId: null,
    verifierName: null,
    verificationStatus: 0,
    verificationStatusText: '未核证',
    createdAt: '2026-05-01 10:00:00',
    updatedAt: '2026-05-01 10:00:00',
    ...overrides,
  }
}

export function createUser(overrides: Record<string, unknown> = {}) {
  return {
    id: 2,
    username: 'enterprise001',
    realName: '张三',
    userType: 1,
    email: 'enterprise001@example.com',
    phone: '13800138001',
    status: 1,
    createdAt: '2026-01-01 00:00:00',
    ...overrides,
  }
}

export function createCreditScore(overrides: Record<string, unknown> = {}) {
  return {
    score: 100,
    level: 'EXCELLENT',
    tradeRestricted: false,
    accountFrozen: false,
    ...overrides,
  }
}

export function createCarbonCoinAccount(overrides: Record<string, unknown> = {}) {
  return {
    balance: 10000,
    totalRecharged: 10000,
    totalSpent: 0,
    ...overrides,
  }
}
