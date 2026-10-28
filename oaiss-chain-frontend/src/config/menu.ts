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
}

export const MENU_BY_ROLE: Record<RoleType, MenuItem[]> = {
  [ROLE.ENTERPRISE]: [
    { label: 'menu.carbonAccounting', path: '/enterprise/carbon/upload' },
    { label: 'menu.p2pOrderManage', path: '/enterprise/orders/manage' },
    {
      label: 'menu.carbonTrading',
      children: [
        { label: 'menu.doubleAuction', path: '/enterprise/trading/market' },
        { label: 'menu.p2pTrading', path: '/enterprise/trading/p2p' },
      ],
    },
    {
      label: 'menu.companyInfo',
      children: [
        { label: 'menu.dataVisualization', path: '/enterprise/company/dashboard' },
        { label: 'menu.enterpriseInfo', path: '/enterprise/info' },
      ],
    },
    { label: 'menu.creditScore', path: '/enterprise/credit/score' },
    { label: 'menu.carbonCoinAccount', path: '/enterprise/carbon-coin/account' },
    { label: 'menu.blockchain', path: '/enterprise/blockchain/browser' },
    {
      label: 'menu.carbonNeutral',
      children: [
        { label: 'menu.carbonNeutralProject', path: '/enterprise/carbon-neutral/projects' },
        { label: 'menu.emissionData', path: '/enterprise/emission/data' },
        { label: 'menu.carbonFormula', path: '/enterprise/carbon-formula' },
      ],
    },
    {
      label: 'menu.aiPrediction',
      children: [
        { label: 'menu.marketPrediction', path: '/enterprise/market-prediction' },
        { label: 'menu.enterpriseInference', path: '/enterprise/enterprise-inference' },
      ],
    },
    { label: 'menu.personalCenter', path: '/enterprise/user/profile' },
  ],
  [ROLE.REVIEWER]: [
    {
      label: 'menu.auditMaterial',
      children: [
        { label: 'menu.carbonEmissionData', path: '/auditor/audit/list' },
        { label: 'menu.reviewHistory', path: '/auditor/review/history' },
      ],
    },
    { label: 'menu.projectReview', path: '/auditor/project/review' },
    { label: 'menu.certificationManage', path: '/auditor/verify/list' },
  ],
  [ROLE.THIRD_PARTY]: [
    { label: 'menu.monitoringCenter', path: '/third-party/monitor' },
  ],
  [ROLE.ADMIN]: [
    {
      label: 'menu.systemManage',
      children: [
        { label: 'menu.userManage', path: '/admin/system/users' },
        { label: 'menu.carbonManage', path: '/admin/system/carbon' },
        { label: 'menu.systemConfig', path: '/admin/system/config' },
      ],
    },
    { label: 'menu.dataManage', path: '/admin/data/statistics' },
    { label: 'menu.certificationManage', path: '/admin/verify/list' },
    { label: 'menu.certificateManage', path: '/admin/certificates' },
  ],
}
