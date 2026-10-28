/** 搜索-碳报告摘要 */
export interface CarbonReportSummary {
  id: number
  reportNo: string
  enterpriseId: number
  title: string
  accountingPeriod: string
  totalEmission: number
  status: number
  createdAt: string
}

/** 搜索-交易摘要 */
export interface TradeSummary {
  id: number
  tradeNo: string
  tradeType: number
  sellerId: number
  buyerId: number
  quantity: number
  unitPrice: number
  totalAmount: number
  status: number
  createdAt: string
}

/** 搜索-市场大盘宏观总览指标 */
export interface MarketOverview {
  /** 已入驻企业数 */
  totalEnterprises: number
  /** 核证/备案碳报告数 */
  totalCarbonReports: number
  /** 链上/撮合交易总数 */
  totalTransactions: number
  /** 碳交易流转总量（吨 CO2 当量） */
  totalTradingVolume?: number
  /** 碳交易累计成交额（元） */
  totalTradeAmount?: number
  /** 累计核证配额/减排总量（吨 CO2 当量） */
  totalEmissionQuota?: number
  /** 活跃区块链节点数 */
  activeNodes?: number
  /** 全网节点数量（兼容大盘展示） */
  nodeCount?: number
  /** 网络运行状态（ONLINE / NORMAL） */
  networkStatus?: string
  /** 分布式高可用率（如 "99.96%" / "99.98%"） */
  networkAvailability?: string
  /** 分布式可用率别名 */
  distributedAvailability?: string
  /** 集中式合规评级（如 "A+"） */
  complianceGrade?: string
  /** 平均延迟（毫秒） */
  avgLatencyMs?: number
  /** 平均延迟别名（毫秒） */
  averageLatencyMs?: number
  /** 最大延时/P99延迟（毫秒） */
  maxLatencyMs?: number
  /** 联盟链连通状态 */
  chainOnline?: boolean
  /** 联盟链运行模式（FABRIC / MOCK） */
  chainMode?: string
  /** 联盟链通道名称 */
  channelName?: string
  /** 最新区块高度 */
  blockHeight?: number
  /** 存证智能合约名称 */
  chaincode?: string
  /** 采样时间戳 (ISO 8601 格式字符串) */
  queryTime?: string
}
