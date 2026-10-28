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
    { label: 'menu.certificationManage', path: '/auditor/verify/list', tab: 'verify', rail: true },
  ],
  [ROLE.THIRD_PARTY]: [
    { label: 'menu.monitoringCenter', path: '/third-party/monitor', tab: 'monitor', rail: true },
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
    { label: 'menu.certificationManage', path: '/admin/verify/list', tab: 'cert', rail: true },
    { label: 'menu.certificateManage', path: '/admin/certificates', tab: 'cert', rail: true },
  ],
}
