export const ROLE = {
  ENTERPRISE: 'ENTERPRISE',
  REVIEWER: 'REVIEWER',
  THIRD_PARTY: 'THIRD_PARTY',
  ADMIN: 'ADMIN',
} as const
export type RoleType = (typeof ROLE)[keyof typeof ROLE]

export const ROLE_LABEL: Record<RoleType, string> = {
  [ROLE.ENTERPRISE]: 'menu.enterprise',
  [ROLE.REVIEWER]: 'menu.reviewer',
  [ROLE.THIRD_PARTY]: 'menu.thirdParty',
  [ROLE.ADMIN]: 'menu.admin',
}

export const ROLE_HOME: Record<RoleType, string> = {
  [ROLE.ENTERPRISE]: '/enterprise/carbon/upload',
  [ROLE.REVIEWER]: '/auditor/audit/list',
  [ROLE.THIRD_PARTY]: '/third-party/monitor',
  [ROLE.ADMIN]: '/admin/system/users',
}

export interface MenuItem {
  label: string
  path?: string
  children?: MenuItem[]
  /** 所属顶栏一级页签（pen 壳层）。仅用于派生导航视图，不改变路由与权限。 */
  tab?: string
  /** 图标栏落位：true = 出现在 72px 图标栏；false/'rail' 缺省 = 仅通过页签内二级进入。 */
  rail?: boolean
}

/**
 * 72px 图标栏条目（对照 pen 设计稿 Rail：64×56，图标 18px + 短标签 9.5px）。
 *
 * 设计约束（这条曾经把导航做塌过，别退化）：
 *   - `rail` 条目必须同时具备 `icon` 与 `railLabel`，否则图标栏会渲染成空白方块；
 *   - `label` 是完整菜单名，同时作为 aria-label —— e2e 的 menuitem 可访问名依赖它；
 *   - 带 `children` 的分组只落一个图标入口，其余叶子由顶栏页签下的二级导航进入，
 *     因此图标栏条目数对齐设计稿的 11 条，而不是叶子路由的 15 条。
 */
export interface RailItem {
  /** 完整菜单名 i18n 键（aria-label 与悬浮提示用） */
  label: string
  /** 图标栏短标签 i18n 键（9.5px，2~3 字） */
  railLabel: string
  /** 点击后进入的路由（分组的首叶子） */
  path: string
  /** Element Plus 图标组件名，由 layout 的图标注册表解析 */
  icon: string
  /** 该入口覆盖的叶子路由，用于高亮判定（分组入口需要覆盖所有子页） */
  match: string[]
}

export const MENU_BY_ROLE: Record<RoleType, MenuItem[]> = {
  [ROLE.ENTERPRISE]: [
    { label: 'menu.carbonAccounting', path: '/enterprise/carbon/upload', tab: 'carbon', rail: true },
    { label: 'menu.p2pOrderManage', path: '/enterprise/orders/manage', tab: 'trade', rail: true },
    {
      label: 'menu.carbonTrading',
      children: [
        { label: 'menu.doubleAuction', path: '/enterprise/trading/market', tab: 'trade', rail: true },
        { label: 'menu.p2pTrading', path: '/enterprise/trading/p2p', tab: 'trade', rail: true },
      ],
    },
    {
      label: 'menu.companyInfo',
      children: [
        { label: 'menu.dataVisualization', path: '/enterprise/company/dashboard', tab: 'asset', rail: true },
        { label: 'menu.enterpriseInfo', path: '/enterprise/info', tab: 'asset', rail: true },
      ],
    },
    { label: 'menu.creditScore', path: '/enterprise/credit/score', tab: 'asset', rail: true },
    { label: 'menu.carbonCoinAccount', path: '/enterprise/carbon-coin/account', tab: 'asset', rail: true },
    { label: 'menu.blockchain', path: '/enterprise/blockchain/browser', tab: 'asset', rail: true },
    {
      label: 'menu.carbonNeutral',
      children: [
        { label: 'menu.carbonNeutralProject', path: '/enterprise/carbon-neutral/projects', tab: 'intel', rail: true },
        { label: 'menu.emissionData', path: '/enterprise/emission/data', tab: 'intel', rail: false },
        { label: 'menu.carbonFormula', path: '/enterprise/carbon-formula', tab: 'intel', rail: false },
      ],
    },
    {
      label: 'menu.aiPrediction',
      children: [
        { label: 'menu.marketPrediction', path: '/enterprise/market-prediction', tab: 'intel', rail: true },
        { label: 'menu.enterpriseInference', path: '/enterprise/enterprise-inference', tab: 'intel', rail: false },
      ],
    },
    { label: 'menu.searchCenter', path: '/search', tab: 'search', rail: true },
    { label: 'menu.personalCenter', path: '/enterprise/user/profile', rail: true },
  ],
  [ROLE.REVIEWER]: [
    {
      label: 'menu.auditMaterial',
      children: [
        { label: 'menu.carbonEmissionData', path: '/auditor/audit/list', tab: 'audit', rail: true },
        { label: 'menu.reviewHistory', path: '/auditor/review/history', tab: 'audit', rail: true },
      ],
    },
    { label: 'menu.projectReview', path: '/auditor/project/review', tab: 'project', rail: true },
    { label: 'menu.tradeAudit', path: '/auditor/trade/audit', tab: 'trade', rail: true },
    { label: 'menu.certificationManage', path: '/auditor/verify/list', tab: 'verify', rail: true },
    { label: 'menu.searchCenter', path: '/search', tab: 'search', rail: true },
  ],
  [ROLE.THIRD_PARTY]: [
    { label: 'menu.monitoringCenter', path: '/third-party/monitor', tab: 'monitor', rail: true },
    { label: 'menu.searchCenter', path: '/search', tab: 'search', rail: true },
  ],
  [ROLE.ADMIN]: [
    {
      label: 'menu.systemManage',
      children: [
        { label: 'menu.userManage', path: '/admin/system/users', tab: 'system', rail: true },
        { label: 'menu.carbonManage', path: '/admin/system/carbon', tab: 'system', rail: true },
        { label: 'menu.systemConfig', path: '/admin/system/config', tab: 'system', rail: true },
      ],
    },
    { label: 'menu.dataManage', path: '/admin/data/statistics', tab: 'data', rail: true },
    { label: 'menu.auctionMatching', path: '/admin/trading/matching', tab: 'trade', rail: true },
    // 成交审核的管理员兜底入口：审核员是主审，管理员只在卡单时补救。
    // 与审核员端指向同一组件，路由允许 REVIEWER|ADMIN（与后端授权一致）。
    { label: 'menu.tradeAudit', path: '/auditor/trade/audit', tab: 'trade', rail: true },
    { label: 'menu.chainOps', path: '/admin/blockchain', tab: 'chain', rail: true },
    // 上链补救统一由审核员端「上链补救」承担，管理员端不再保留功能重复的「认证列表」入口。
    { label: 'menu.certificateManage', path: '/admin/certificates', tab: 'cert', rail: true },
    { label: 'menu.searchCenter', path: '/search', tab: 'search', rail: true },
  ],
}

/**
 * 图标栏条目表：与 MENU_BY_ROLE 的叶子路由一一对应，但按 pen 稿收敛为图标入口。
 * 条目数（企业 11 / 审核 4 / 监管 1 / 管理 6）即设计稿的图标栏规模。
 */
export const RAIL_BY_ROLE: Record<RoleType, RailItem[]> = {
  [ROLE.ENTERPRISE]: [
    { label: 'menu.carbonAccounting', railLabel: 'menu.railFill', path: '/enterprise/carbon/upload', icon: 'DataAnalysis', match: ['/enterprise/carbon/upload'] },
    { label: 'menu.p2pOrderManage', railLabel: 'menu.railOrder', path: '/enterprise/orders/manage', icon: 'Tickets', match: ['/enterprise/orders/manage'] },
    { label: 'menu.doubleAuction', railLabel: 'menu.railAuction', path: '/enterprise/trading/market', icon: 'Coin', match: ['/enterprise/trading/market'] },
    { label: 'menu.p2pTrading', railLabel: 'menu.railP2P', path: '/enterprise/trading/p2p', icon: 'Switch', match: ['/enterprise/trading/p2p'] },
    { label: 'menu.companyInfo', railLabel: 'menu.railBoard', path: '/enterprise/company/dashboard', icon: 'TrendCharts', match: ['/enterprise/company/dashboard', '/enterprise/info'] },
    { label: 'menu.creditScore', railLabel: 'menu.railCredit', path: '/enterprise/credit/score', icon: 'Medal', match: ['/enterprise/credit/score'] },
    { label: 'menu.carbonCoinAccount', railLabel: 'menu.railCoin', path: '/enterprise/carbon-coin/account', icon: 'Wallet', match: ['/enterprise/carbon-coin/account'] },
    { label: 'menu.blockchain', railLabel: 'menu.railChain', path: '/enterprise/blockchain/browser', icon: 'Link', match: ['/enterprise/blockchain/browser'] },
    { label: 'menu.carbonNeutral', railLabel: 'menu.railNeutral', path: '/enterprise/carbon-neutral/projects', icon: 'Aim', match: ['/enterprise/carbon-neutral/projects'] },
    { label: 'menu.aiPrediction', railLabel: 'menu.railAI', path: '/enterprise/market-prediction', icon: 'Cpu', match: ['/enterprise/market-prediction', '/enterprise/enterprise-inference'] },
    { label: 'menu.searchCenter', railLabel: 'menu.railSearch', path: '/search', icon: 'Search', match: ['/search'] },
    { label: 'menu.personalCenter', railLabel: 'menu.railMine', path: '/enterprise/user/profile', icon: 'User', match: ['/enterprise/user/profile'] },
  ],
  [ROLE.REVIEWER]: [
    { label: 'menu.auditMaterial', railLabel: 'menu.railAudit', path: '/auditor/audit/list', icon: 'Document', match: ['/auditor/audit/list', '/auditor/review/history'] },
    { label: 'menu.projectReview', railLabel: 'menu.railProject', path: '/auditor/project/review', icon: 'Stamp', match: ['/auditor/project/review'] },
    { label: 'menu.tradeAudit', railLabel: 'menu.railTradeAudit', path: '/auditor/trade/audit', icon: 'Wallet', match: ['/auditor/trade/audit'] },
    { label: 'menu.certificationManage', railLabel: 'menu.railCert', path: '/auditor/verify/list', icon: 'Key', match: ['/auditor/verify/list'] },
    { label: 'menu.searchCenter', railLabel: 'menu.railSearch', path: '/search', icon: 'Search', match: ['/search'] },
  ],
  [ROLE.THIRD_PARTY]: [
    { label: 'menu.monitoringCenter', railLabel: 'menu.railMonitor', path: '/third-party/monitor', icon: 'Monitor', match: ['/third-party/monitor'] },
    { label: 'menu.searchCenter', railLabel: 'menu.railSearch', path: '/search', icon: 'Search', match: ['/search'] },
  ],
  [ROLE.ADMIN]: [
    { label: 'menu.systemManage', railLabel: 'menu.railSystem', path: '/admin/system/users', icon: 'Setting', match: ['/admin/system/users', '/admin/system/carbon', '/admin/system/config'] },
    { label: 'menu.dataManage', railLabel: 'menu.railData', path: '/admin/data/statistics', icon: 'DataBoard', match: ['/admin/data/statistics'] },
    { label: 'menu.auctionMatching', railLabel: 'menu.railMatching', path: '/admin/trading/matching', icon: 'Histogram', match: ['/admin/trading/matching'] },
    { label: 'menu.chainOps', railLabel: 'menu.railChainOps', path: '/admin/blockchain', icon: 'Link', match: ['/admin/blockchain'] },
    { label: 'menu.certificateManage', railLabel: 'menu.railCertAdmin', path: '/admin/certificates', icon: 'Files', match: ['/admin/certificates'] },
    { label: 'menu.searchCenter', railLabel: 'menu.railSearch', path: '/search', icon: 'Search', match: ['/search'] },
  ],
}