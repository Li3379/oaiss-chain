import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { pinia, useAppStore } from '../store'
import { ROLE } from '../config/menu'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: () => {
      const appStore = useAppStore(pinia)
      if (appStore.loggedIn && appStore.homePath) {
        return appStore.homePath
      }
      return '/official-home'
    },
  },
  {
    path: '/official-home',
    name: 'OfficialHome',
    component: () => import('../views/OfficialHome.vue'),
    meta: { public: true, keepWhenLoggedIn: true, title: '官方网站首页' },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('../layout/index.vue'),
    children: [
      {
        path: 'enterprise/carbon/upload',
        name: 'EnterpriseCarbonUpload',
        component: () => import('../views/enterprise/CarbonUpload.vue'),
        meta: { title: '上传审核', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/orders/manage',
        name: 'EnterpriseOrdersManage',
        component: () => import('../views/enterprise/OrdersManage.vue'),
        meta: { title: '订单管理', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/trading/market',
        name: 'EnterpriseTradingMarket',
        component: () => import('../views/enterprise/TradingMarket.vue'),
        meta: { title: '双向拍卖', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/trading/p2p',
        name: 'EnterpriseTradingP2P',
        component: () => import('../views/enterprise/TradingP2P.vue'),
        meta: { title: 'P2P交易', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/company/dashboard',
        name: 'EnterpriseCompanyDashboard',
        component: () => import('../views/enterprise/CompanyDashboard.vue'),
        meta: { title: '数据可视化', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/credit/score',
        name: 'EnterpriseCreditScore',
        component: () => import('../views/enterprise/CreditScore.vue'),
        meta: { title: '信誉评分', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/carbon-coin/account',
        name: 'EnterpriseCarbonCoin',
        component: () => import('../views/enterprise/CarbonCoin.vue'),
        meta: { title: '碳币账户', roles: [ROLE.ENTERPRISE] },
      },
      {
        // 区块浏览器归属监管与管理员：含区块列表、块内交易、MSP、背书节点等溯源信息。
        // 组件文件保留在 views/enterprise/Blockchain.vue 原地，仅调整可见角色。
        path: 'enterprise/blockchain/browser',
        name: 'EnterpriseBlockchain',
        component: () => import('../views/enterprise/Blockchain.vue'),
        meta: { title: '区块链浏览器', roles: [ROLE.ADMIN, ROLE.THIRD_PARTY] },
      },
      {
        path: 'enterprise/carbon-neutral/projects',
        name: 'EnterpriseCarbonNeutral',
        component: () => import('../views/enterprise/CarbonNeutral.vue'),
        meta: { title: '碳中和项目', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/carbon-neutral/projects/:id',
        name: 'EnterpriseCarbonNeutralDetail',
        component: () => import('../views/enterprise/CarbonNeutralDetail.vue'),
        meta: { title: '项目详情', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/emission/data',
        name: 'EnterpriseEmissionData',
        component: () => import('../views/enterprise/EmissionData.vue'),
        meta: { title: '排放数据', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/user/profile',
        name: 'EnterpriseUserProfile',
        component: () => import('../views/enterprise/UserProfile.vue'),
        meta: { title: '个人中心', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/market-prediction',
        name: 'EnterpriseMarketPrediction',
        component: () => import('../views/enterprise/MarketPrediction.vue'),
        meta: { title: 'AI市场预测', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/carbon-formula',
        name: 'EnterpriseCarbonFormula',
        component: () => import('../views/enterprise/CarbonFormulaCalculator.vue'),
        meta: { title: '碳核算公式', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'enterprise/info',
        name: 'EnterpriseInfo',
        component: () => import('../views/enterprise/EnterpriseInfo.vue'),
        meta: { title: '企业信息', roles: [ROLE.ENTERPRISE] },
      },
      {
        path: 'auditor/audit/list',
        name: 'AuditorAuditList',
        component: () => import('../views/auditor/AuditList.vue'),
        meta: { title: '碳排放数据', roles: [ROLE.REVIEWER] },
      },
      {
        path: 'auditor/review/history',
        name: 'AuditorReviewHistory',
        component: () => import('../views/auditor/ReviewHistory.vue'),
        meta: { title: '审核历史', roles: [ROLE.REVIEWER] },
      },
      {
        path: 'auditor/project/review',
        name: 'AuditorProjectReview',
        component: () => import('../views/auditor/ProjectReview.vue'),
        meta: { title: '项目审核', roles: [ROLE.REVIEWER] },
      },
      {
        // 成交审核：审核员主审，管理员兜底（与后端 TradeAuditController 授权一致）。
        // 待审成交在审核通过前不结算、不上链，因此本页是资金与额度真正落地的关口。
        path: 'auditor/trade/audit',
        name: 'AuditorTradeAudit',
        component: () => import('../views/auditor/TradeAudit.vue'),
        meta: { title: '成交审核', roles: [ROLE.REVIEWER, ROLE.ADMIN] },
      },
      {
        // 方案 A：上链触发者 = 审核员审核通过（系统自动上链）。
        // 本页面是「自动上链失败的幂等补救」的**唯一入口**（ADMIN 仅保留直接 URL 兜底，
        // 不再提供独立的管理员页面，避免同一组件出现两个功能重复的菜单项）。
        path: 'auditor/verify/list',
        name: 'AuditorVerifyList',
        component: () => import('../views/admin/VerifyList.vue'),
        meta: { title: '上链补救', roles: [ROLE.REVIEWER, ROLE.ADMIN] },
      },
      {
        path: 'third-party/monitor',
        name: 'ThirdPartyMonitor',
        component: () => import('../views/third-party/Monitor.vue'),
        meta: { title: '监管面板', roles: [ROLE.THIRD_PARTY] },
      },
      {
        // 需求 3.3.2 市场信息查询入口：四角色共用，企业/审核员检索到匿名交易记录，
        // 管理员/第三方监管可按真实主体穿透核查（脱敏口径由后端按角色决定）。
        path: 'search',
        name: 'Search',
        component: () => import('../views/Search.vue'),
        meta: { title: '搜索查询', roles: [ROLE.ENTERPRISE, ROLE.REVIEWER, ROLE.THIRD_PARTY, ROLE.ADMIN] },
      },
      {
        path: 'admin/system/users',
        name: 'AdminSystemUsers',
        component: () => import('../views/admin/SystemUsers.vue'),
        meta: { title: '用户管理', roles: [ROLE.ADMIN] },
      },
      {
        path: 'admin/system/carbon',
        name: 'AdminSystemCarbon',
        component: () => import('../views/admin/SystemCarbon.vue'),
        meta: { title: '碳核算管理', roles: [ROLE.ADMIN] },
      },
      {
        path: 'admin/system/config',
        name: 'AdminSystemConfig',
        component: () => import('../views/admin/SystemConfig.vue'),
        meta: { title: '系统配置', roles: [ROLE.ADMIN] },
      },
      {
        path: 'admin/data/statistics',
        name: 'AdminDataStatistics',
        component: () => import('../views/admin/DataStatistics.vue'),
        meta: { title: '统计数据', roles: [ROLE.ADMIN] },
      },
      {
        path: 'admin/certificates',
        name: 'AdminCertificates',
        component: () => import('../views/admin/CertificateManage.vue'),
        meta: { title: '证书管理', roles: [ROLE.ADMIN] },
      },
      {
        // 链上管理：对账 + 死信重放 + 只读浏览。刻意不提供任何改链操作（账本不可改）。
        path: 'admin/blockchain',
        name: 'AdminChainOps',
        component: () => import('../views/admin/ChainOps.vue'),
        meta: { title: '链上管理', roles: [ROLE.ADMIN] },
      },
      {
        // 撮合控制台：平台默认每 30 秒自动撮合，本页面用于观察运行态 + 手动立即出清一轮。
        path: 'admin/trading/matching',
        name: 'AdminAuctionMatching',
        component: () => import('../views/admin/AuctionMatching.vue'),
        meta: { title: '撮合控制台', roles: [ROLE.ADMIN] },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue'), meta: { title: '页面未找到' } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const appStore = useAppStore(pinia)

  if (to.name === 'NotFound') return true
  if (appStore.loggedIn && !appStore.role) {
    appStore.logout()
    if (to.meta.public) return true
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && appStore.loggedIn && !to.meta.keepWhenLoggedIn) {
    return appStore.homePath
  }
  if (to.meta.public) return true
  if (!appStore.loggedIn) return { path: '/login', query: { redirect: to.fullPath } }
  if (Array.isArray(to.meta.roles) && to.meta.roles.length > 0) {
    if (!to.meta.roles.includes(appStore.role)) return appStore.homePath
  }
  return true
})

export default router
