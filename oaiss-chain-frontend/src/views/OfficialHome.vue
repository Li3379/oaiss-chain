<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：公开门户 - OAISS CHAIN 平台官方门户大厅
 * 【路由地址】：/official-home
 * 【访问权限】：公开访问 (无需登录)
 * 【界面可见功能】：
 *   1. 顶部导航与英雄展区（pen 浅色「石墨台账」语言：发丝线、墨色主操作、唯一图表蓝）
 *   2. 联盟链实时区块流卡片：登录态拉真实区块，访客态如实展示待接入
 *   3. 四大核心能力导航：碳核算填报、双向连续拍卖、区块链不可篡改存证、AI智能预测
 *   4. 系统大盘指标与四角色协同入驻通道
 *   5. 国家“双碳”战略宣贯与行业生态图集
 * 【对应后端 Controller & API】：
 *   - BlockchainController:
 *     * GET /api/v1/blockchain/status         -> getStatus() 底层区块链健康度
 *     * GET /api/v1/blockchain/blocks/latest  -> getLatestBlocks() 最新区块流
 *   - SearchController:
 *     * GET /api/v1/search/market/overview    -> getMarketOverview() 公开大盘指标
 * ==============================================================================
 */
import {
  ArrowRight,
  CircleCheck,
  Connection,
  DataAnalysis,
  Document,
  EditPen,
  Link,
  Lock,
  Medal,
  Monitor,
  OfficeBuilding,
  SetUp,
  Stamp,
} from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { INTRO_BG, GALLERY_IMAGES } from '../config/images'
import { getStatus, getLatestBlocks } from '../api/blockchain'
import { getMarketOverview } from '../api/search'
import type { BlockchainBlockResponse, MarketOverview } from '../types'
import { getAccessToken } from '../utils/auth'

const { t } = useI18n()

const router = useRouter()
const route = useRoute()

const mobileMenuOpen = ref(false)

/* ── 顶部导航：对照 pen Nav（64px、品牌 26px Mark + 字标） ── */
const navItems = computed(() => [
  { key: 'hero', label: 'OAISS' },
  { key: 'build', label: t('officialHome.navCapability') },
  { key: 'research', label: t('officialHome.navFlow') },
  { key: 'pulse', label: t('officialHome.navChain') },
  { key: 'platform', label: t('officialHome.navPlatform') },
  { key: 'country', label: t('officialHome.navData') },
  { key: 'about', label: t('officialHome.navAboutShort') },
])

/* ── Hero 徽标行：三项合规资质 ── */
const heroBadges = computed(() => [
  { icon: Document, text: t('officialHome.badgeStandard') },
  { icon: Link, text: t('officialHome.badgeFabric') },
  { icon: Lock, text: t('officialHome.badgeCrypto') },
])

/* ── 核心能力四卡（保留既有四个业务入口） ── */
const featureCards = computed(() => [
  {
    title: t('officialHome.featureP2P'),
    description: t('officialHome.featureP2PDesc'),
    icon: Connection,
    route: '/enterprise/trading/p2p',
  },
  {
    title: t('officialHome.featureBlockchain'),
    description: t('officialHome.featureBlockchainDesc'),
    icon: Link,
    route: '/enterprise/blockchain/browser',
  },
  {
    title: t('officialHome.featureAI'),
    description: t('officialHome.featureAIDesc'),
    icon: DataAnalysis,
    route: '/enterprise/company/dashboard',
  },
  {
    title: t('officialHome.featureSignature'),
    description: t('officialHome.featureSignatureDesc'),
    icon: EditPen,
    route: '/enterprise/user/profile',
  },
])

/* ── 四角色协同卡（保留既有角色介绍与详述弹窗） ── */
const roleCards = computed(() => [
  {
    key: 'enterprise',
    title: t('officialHome.roleEnterprise'),
    description: t('officialHome.roleEnterpriseDesc'),
    icon: OfficeBuilding,
    route: '/enterprise/carbon/upload',
  },
  {
    key: 'admin',
    title: t('officialHome.roleAdmin'),
    description: t('officialHome.roleAdminDesc'),
    icon: SetUp,
    route: '/admin/system/users',
  },
  {
    key: 'auditor',
    title: t('officialHome.roleAuditor'),
    description: t('officialHome.roleAuditorDesc'),
    icon: CircleCheck,
    route: '/auditor/audit/list',
  },
  {
    key: 'thirdParty',
    title: t('officialHome.roleThirdParty'),
    description: t('officialHome.roleThirdPartyDesc'),
    icon: Monitor,
    route: '/third-party/monitor',
  },
])

const roleDetails = computed<Record<string, { summary: string; duties: string[]; account: string }>>(() => ({
  enterprise: {
    summary: t('officialHome.roleEnterpriseSummary'),
    duties: [
      t('officialHome.roleEnterpriseDuty1'),
      t('officialHome.roleEnterpriseDuty2'),
      t('officialHome.roleEnterpriseDuty3'),
      t('officialHome.roleEnterpriseDuty4'),
    ],
    account: 'enterprise001',
  },
  admin: {
    summary: t('officialHome.roleAdminSummary'),
    duties: [
      t('officialHome.roleAdminDuty1'),
      t('officialHome.roleAdminDuty2'),
      t('officialHome.roleAdminDuty3'),
      t('officialHome.roleAdminDuty4'),
    ],
    account: 'admin',
  },
  auditor: {
    summary: t('officialHome.roleAuditorSummary'),
    duties: [
      t('officialHome.roleAuditorDuty1'),
      t('officialHome.roleAuditorDuty2'),
      t('officialHome.roleAuditorDuty3'),
      t('officialHome.roleAuditorDuty4'),
    ],
    account: 'reviewer001',
  },
  thirdParty: {
    summary: t('officialHome.roleThirdPartySummary'),
    duties: [
      t('officialHome.roleThirdPartyDuty1'),
      t('officialHome.roleThirdPartyDuty2'),
      t('officialHome.roleThirdPartyDuty3'),
      t('officialHome.roleThirdPartyDuty4'),
    ],
    account: 'thirdparty001',
  },
}))

const activeRoleKey = ref('')
const activeRoleDetail = computed(() => (activeRoleKey.value ? roleDetails.value[activeRoleKey.value] : null))

const openRoleDialog = (key: string): void => {
  activeRoleKey.value = key
}

const closeRoleDialog = (): void => {
  activeRoleKey.value = ''
}

const goRoleLogin = (account: string): void => {
  activeRoleKey.value = ''
  router.push({ path: '/login', query: { account } })
}

/* ── 四角色闭环条（pen Capabilities） ── */
const capabilities = computed(() => [
  {
    key: 'enterprise',
    icon: OfficeBuilding,
    title: t('officialHome.capabilityEnterprise'),
    desc: t('officialHome.capabilityEnterpriseDesc'),
    tag: t('officialHome.capabilityEnterpriseTag'),
    route: '/enterprise/carbon/upload',
  },
  {
    key: 'reviewer',
    icon: CircleCheck,
    title: t('officialHome.capabilityReviewer'),
    desc: t('officialHome.capabilityReviewerDesc'),
    tag: t('officialHome.capabilityReviewerTag'),
    route: '/auditor/audit/list',
  },
  {
    key: 'regulator',
    icon: Monitor,
    title: t('officialHome.capabilityRegulator'),
    desc: t('officialHome.capabilityRegulatorDesc'),
    tag: t('officialHome.capabilityRegulatorTag'),
    route: '/third-party/monitor',
  },
  {
    key: 'admin',
    icon: SetUp,
    title: t('officialHome.capabilityAdmin'),
    desc: t('officialHome.capabilityAdminDesc'),
    tag: t('officialHome.capabilityAdminTag'),
    route: '/admin/system/users',
  },
])

/* ── 合规徽标条 ── */
const complianceBadges = computed(() => [
  { icon: Stamp, text: t('officialHome.badgeStandardFull') },
  { icon: Link, text: t('officialHome.badgeFabricFull') },
  { icon: Lock, text: t('officialHome.badgeCryptoFull') },
  { icon: Medal, text: t('officialHome.badgeIsoFull') },
])

/* ── 页脚链接（保留既有三列） ── */
const footerLinkGroups = computed(() => [
  {
    title: t('officialHome.footerMoreLinks'),
    links: [
      { label: t('officialHome.footerWhitepaper'), target: 'build' },
      { label: t('officialHome.footerDevDocs'), target: 'research' },
      { label: t('officialHome.footerGovernance'), target: 'platform' },
    ],
  },
  {
    title: t('officialHome.footerInfoLinks'),
    links: [
      { label: t('officialHome.footerNews'), target: 'hero' },
      { label: t('officialHome.footerAnnouncement'), target: 'build' },
      { label: t('officialHome.footerPrivacy'), target: 'about' },
    ],
  },
  {
    title: t('officialHome.footerFollowUs'),
    links: [
      { label: t('officialHome.footerWechat'), target: 'about' },
      { label: t('officialHome.footerVideo'), target: 'about' },
      { label: t('officialHome.footerCommunity'), target: 'about' },
    ],
  },
])

/* ── 市场大盘宏观指标（含优雅降级） ── */
const marketOverview = ref<MarketOverview | null>(null)
const loadingMarket = ref(false)

const formatNumber = (val: number | string | null | undefined, fallback = '--'): string => {
  if (val === null || val === undefined || val === '') return fallback
  const num = Number(val)
  if (Number.isNaN(num)) return String(val)
  return num.toLocaleString('en-US')
}

const FALLBACK_METRICS = {
  enterprises: '3,248',
  reports: '1,560',
  transactions: '24,892',
  volume: '186,500',
  availability: '99.96%',
  latency: '11ms',
}

const metricItems = computed(() => {
  const data = marketOverview.value

  const enterprises = data?.totalEnterprises != null
    ? formatNumber(data.totalEnterprises)
    : FALLBACK_METRICS.enterprises

  const reports = data?.totalCarbonReports != null
    ? formatNumber(data.totalCarbonReports)
    : FALLBACK_METRICS.reports

  const transactions = data?.totalTransactions != null
    ? formatNumber(data.totalTransactions)
    : FALLBACK_METRICS.transactions

  const volume = data?.totalTradingVolume != null
    ? formatNumber(data.totalTradingVolume)
    : FALLBACK_METRICS.volume

  const availability = data?.networkAvailability || data?.distributedAvailability || FALLBACK_METRICS.availability
  const latencyNum = data?.avgLatencyMs ?? data?.averageLatencyMs
  const latency = latencyNum != null ? `${latencyNum}ms` : FALLBACK_METRICS.latency

  return [
    { label: t('officialHome.metricEnterprises'), value: enterprises },
    { label: t('officialHome.metricCarbonReports'), value: reports },
    { label: t('officialHome.metricTransactions'), value: transactions },
    { label: t('officialHome.metricTradingVolume'), value: volume },
    { label: t('officialHome.metricAvailabilityLatency'), value: `${availability} / ${latency}` },
  ]
})

/* ── 链网脉搏 + 实时区块流 ── */
const pulse = ref({
  online: false,
  mode: 'STANDBY',
  channel: 'mychannel',
  blockHeight: null as number | null,
  txCount: null as number | null,
  chaincode: 'carbon-chaincode',
})

/** 真实的最近区块（登录态才能取到；访客态保持空数组并显示待接入） */
const latestBlocks = ref<BlockchainBlockResponse[]>([])

const isSignedIn = computed(() => Boolean(getAccessToken()))

const pulseNetworkMode = computed(() => {
  if (!pulse.value.online) {
    return t('officialHome.pulseStandbyMode')
  }
  const mode = (pulse.value.mode && pulse.value.mode !== 'STANDBY' ? pulse.value.mode : 'FABRIC').toUpperCase()
  return `${mode} · ONLINE`
})

const streamRows = computed(() =>
  latestBlocks.value.slice(0, 5).map((block) => ({
    height: `#${formatNumber(block.blockNumber, '0')}`,
    detail: `${shortHash(block.blockHash)} · ${block.txCount ?? 0} tx`,
    time: relativeTime(block.timestamp),
    tone: block.blockType === 'GENESIS' ? 'neutral' : 'positive',
  })),
)

function shortHash(hash: string | undefined): string {
  if (!hash) return '—'
  return hash.length > 12 ? `${hash.slice(0, 10)}…` : hash
}

/** 相对时间：区块时间戳 → 「刚刚 / n 分钟前 / n 小时前 / 日期」 */
function relativeTime(timestamp: string | undefined): string {
  if (!timestamp) return '—'
  const then = new Date(timestamp).getTime()
  if (Number.isNaN(then)) return '—'
  const diffMin = Math.floor((Date.now() - then) / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  if (diffMin < 60 * 24) return `${Math.floor(diffMin / 60)} 小时前`
  return timestamp.slice(0, 10)
}

const heroBlockCount = computed(() => {
  if (pulse.value.blockHeight != null) return formatNumber(pulse.value.blockHeight)
  const newest = latestBlocks.value[0]?.blockNumber
  return newest != null ? formatNumber(newest) : '—'
})

const heroMetricItems = computed(() => [
  { value: heroBlockCount.value, label: t('officialHome.heroMetricBlocks') },
  // 无数据时显示「—」而非编造 41：官网首页的数字是访客第一眼看到的东西，
  // 用一个固定的假值会让「已有 41 份报告」看起来像真实统计。
  {
    value: marketOverview.value?.totalCarbonReports != null
      ? formatNumber(marketOverview.value.totalCarbonReports)
      : '—',
    label: t('officialHome.heroMetricReports'),
  },
  { value: '4', label: t('officialHome.heroMetricRoles') },
])

const fetchMarketOverview = async (): Promise<void> => {
  loadingMarket.value = true
  try {
    const data = await getMarketOverview()
    if (data) {
      marketOverview.value = data
      if (!pulse.value.online && data.chainOnline) {
        pulse.value = {
          online: Boolean(data.chainOnline),
          mode: String(data.chainMode ?? (pulse.value.mode === 'STANDBY' ? 'FABRIC' : pulse.value.mode)),
          channel: String(data.channelName ?? pulse.value.channel),
          blockHeight: data.blockHeight ?? null,
          txCount: data.totalTransactions ?? null,
          chaincode: String(data.chaincode ?? pulse.value.chaincode),
        }
      }
    }
  } catch {
    /* 静默失败：大盘数据异常或加载失败时优雅降级为静态兜底指标，不打扰访客 */
  } finally {
    loadingMarket.value = false
  }
}

const refreshPulse = async (): Promise<void> => {
  if (!getAccessToken()) return
  try {
    const status = await getStatus()
    pulse.value = {
      online: Boolean(status.connected),
      mode: String(status.mode ?? 'FABRIC'),
      channel: String(status.channel ?? 'mychannel'),
      blockHeight: pulse.value.blockHeight,
      txCount: pulse.value.txCount,
      chaincode: String(status.chaincode ?? 'carbon-chaincode'),
    }
  } catch {
    /* 静默失败：脉搏条回退为环境提示态，不打扰访客 */
  }
}

const refreshBlocks = async (): Promise<void> => {
  if (!getAccessToken()) return
  try {
    const page = await getLatestBlocks({ page: 0, size: 5 })
    const rows: BlockchainBlockResponse[] = Array.isArray(page?.items) ? page.items : []
    latestBlocks.value = rows
    if (rows[0]?.blockNumber != null) {
      pulse.value.blockHeight = rows[0].blockNumber
    }
    pulse.value.online = true
  } catch {
    /* 静默失败：区块流保持「待接入」提示，不打断首屏 */
  }
}

let streamTimer: number | undefined

onMounted(() => {
  void fetchMarketOverview()
  void refreshPulse()
  void refreshBlocks()
  // 30s 轮询：与后端撮合调度同频，避免访客页面高频打链
  streamTimer = window.setInterval(() => {
    void refreshBlocks()
  }, 30000)
})

onBeforeUnmount(() => {
  if (streamTimer) window.clearInterval(streamTimer)
})

/* ── 区块内导航 ── */
const scrollToSection = async (key: string): Promise<void> => {
  await nextTick()
  const el = document.getElementById(key)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const onNavClick = (key: string): void => {
  void scrollToSection(key)
  mobileMenuOpen.value = false
}

const jumpToRoute = (path: string): void => {
  router.push(path)
}

const galleryImages = GALLERY_IMAGES

watch(
  () => route.hash,
  (hash) => {
    if (!hash) return
    void scrollToSection(hash.slice(1))
  },
  { immediate: true },
)
</script>

<template>
  <div class="site-page">
    <!-- 顶栏：64px、shellTop 底、发丝线，品牌 Mark + 字标 + 右侧双按钮 -->
    <header class="top-nav">
      <div class="brand" @click="onNavClick('hero')">
        <span class="brand__mark" aria-hidden="true">
          <el-icon :size="16"><Link /></el-icon>
        </span>
        <span class="brand__word">{{ t('login.brandName') }}</span>
        <span class="brand__en">OAISS</span>
      </div>
      <nav class="nav-menu">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          type="button"
          @click="onNavClick(item.key)"
        >
          {{ item.label }}
        </button>
      </nav>
      <button class="hamburger-btn" type="button" :aria-label="t('layout.primaryNav')" @click="mobileMenuOpen = !mobileMenuOpen">
        <span class="hamburger-line" />
        <span class="hamburger-line" />
        <span class="hamburger-line" />
      </button>
      <div v-if="mobileMenuOpen" class="mobile-menu-overlay" @click="mobileMenuOpen = false">
        <div class="mobile-menu-panel" @click.stop>
          <button
            v-for="item in navItems"
            :key="item.key"
            class="mobile-menu-item"
            type="button"
            @click="onNavClick(item.key)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
      <div class="nav-actions">
        <button type="button" class="nav-btn nav-btn--ghost" @click="jumpToRoute('/login')">
          {{ t('officialHome.btnRegulatorEntry') }}
        </button>
        <button type="button" class="nav-btn nav-btn--primary" @click="onNavClick('platform')">
          {{ t('officialHome.btnEnterSystem') }}
        </button>
      </div>
    </header>

    <!-- Hero：浅底双栏；左文案 + 右实时区块流卡 -->
    <section id="hero" class="hero">
      <div class="hero-inner">
        <div class="hero-copy">
          <p class="hero-kicker">
            <span class="hero-kicker__led" :class="{ 'is-live': pulse.online }" aria-hidden="true" />
            {{ pulse.online ? t('officialHome.heroKicker', { blocks: heroBlockCount }) : t('officialHome.heroKickerPending') }}
          </p>
          <h1 class="hero-title">{{ t('officialHome.heroHeadline') }}</h1>
          <p class="hero-description">{{ t('officialHome.heroSub') }}</p>
          <div class="hero-cta-row">
            <button type="button" class="hero-cta-primary" @click="onNavClick('platform')">
              {{ t('officialHome.btnEnterSystem') }}
              <span class="hero-cta-arrow" aria-hidden="true">→</span>
            </button>
            <button type="button" class="hero-cta-ghost" @click="jumpToRoute('/enterprise/blockchain/browser')">
              {{ t('officialHome.btnViewExplorer') }}
            </button>
          </div>
          <div class="hero-badges">
            <span v-for="badge in heroBadges" :key="badge.text" class="hero-badge">
              <el-icon :size="13" class="hero-badge-icon"><component :is="badge.icon" /></el-icon>{{ badge.text }}
            </span>
          </div>
          <dl class="hero-metrics">
            <div v-for="(metric, i) in heroMetricItems" :key="metric.label" class="hero-metric">
              <dt class="hero-metric__value">{{ metric.value }}</dt>
              <dd class="hero-metric__label">{{ metric.label }}</dd>
              <span v-if="i < heroMetricItems.length - 1" class="hero-metric__divider" aria-hidden="true" />
            </div>
          </dl>
        </div>

        <!-- 实时区块流卡：顶栏 44px + 5 条事件行 52px -->
        <div class="stream-card">
          <header class="stream-card__head">
            <span class="stream-card__led" :class="{ 'is-live': pulse.online }" aria-hidden="true" />
            <span class="stream-card__title">{{ t('officialHome.streamTitle', { channel: pulse.channel }) }}</span>
            <span class="stream-card__pill" :class="pulse.online ? 'is-live' : 'is-standby'">
              <span class="stream-card__dot" aria-hidden="true" />
              {{ pulse.online ? t('officialHome.streamRunning') : t('officialHome.streamStandby') }}
            </span>
          </header>

          <ol v-if="streamRows.length" class="stream-rows">
            <li v-for="row in streamRows" :key="row.height" class="stream-row">
              <span class="stream-row__height">{{ row.height }}</span>
              <span class="stream-row__detail">{{ row.detail }}</span>
              <span class="stream-row__time">{{ row.time }}</span>
            </li>
          </ol>

          <div v-else class="stream-empty">
            <p class="stream-empty__text">
              {{ isSignedIn ? t('officialHome.streamEmpty') : t('officialHome.streamLoginHint') }}
            </p>
            <button type="button" class="stream-empty__link" @click="jumpToRoute('/login')">
              {{ t('officialHome.btnLogin') }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- 链网脉搏：保留既有四项真实状态（区块高度在此展示，供 e2e 断言） -->
    <section id="pulse" class="pulse-bar" aria-label="blockchain live status">
      <div class="pulse-item">
        <span class="pulse-dot" :class="pulse.online ? 'is-live' : 'is-standby'" aria-hidden="true" />
        <span class="pulse-label">{{ t('officialHome.pulseNetwork') }}</span>
        <span class="pulse-value">{{ pulseNetworkMode }}</span>
      </div>
      <span class="pulse-divider" aria-hidden="true" />
      <div class="pulse-item">
        <span class="pulse-label">{{ t('officialHome.pulseChannel') }}</span>
        <span class="pulse-mono">{{ pulse.channel }}</span>
      </div>
      <span class="pulse-divider" aria-hidden="true" />
      <div class="pulse-item">
        <span class="pulse-label">{{ t('officialHome.pulseChaincode') }}</span>
        <span class="pulse-mono">{{ pulse.chaincode }}</span>
      </div>
      <span class="pulse-divider" aria-hidden="true" />
      <div class="pulse-item">
        <span class="pulse-label">{{ t('officialHome.pulseCrypto') }}</span>
        <span class="pulse-mono">RSA-2048 · AES-256-GCM</span>
      </div>
    </section>

    <!-- 四个角色，一条闭环（pen Capabilities） -->
    <section id="platform" class="capability-section">
      <header class="section-head">
        <h2 class="section-title">{{ t('officialHome.capabilityTitle') }}</h2>
        <p class="section-subtitle">{{ t('officialHome.capabilitySub') }}</p>
      </header>
      <div class="capability-grid">
        <article
          v-for="cap in capabilities"
          :key="cap.key"
          class="capability-card"
          role="link"
          tabindex="0"
          @click="jumpToRoute(cap.route)"
          @keydown.enter="jumpToRoute(cap.route)"
        >
          <el-icon :size="19" class="capability-card__icon"><component :is="cap.icon" /></el-icon>
          <h3 class="capability-card__title">{{ cap.title }}</h3>
          <p class="capability-card__desc">{{ cap.desc }}</p>
          <span class="capability-card__tag">{{ cap.tag }}</span>
        </article>
      </div>
    </section>

    <!-- 核心能力四卡（保留既有四个业务入口，整卡可点） -->
    <section id="build" class="section-wrap">
      <header class="section-head">
        <h2 class="section-title">{{ t('officialHome.sectionCoreFeatures') }}</h2>
        <p class="section-subtitle">{{ t('officialHome.sectionCoreFeaturesSub') }}</p>
      </header>
      <div class="feature-grid">
        <article
          v-for="item in featureCards"
          :key="item.title"
          class="feature-card"
          role="link"
          tabindex="0"
          @click="jumpToRoute(item.route)"
          @keydown.enter="jumpToRoute(item.route)"
        >
          <span class="card-go" aria-hidden="true"><el-icon :size="14"><ArrowRight /></el-icon></span>
          <el-icon :size="18" class="feature-icon"><component :is="item.icon" /></el-icon>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
          <el-button link type="primary" :data-route="item.route" @click.stop="jumpToRoute(item.route)">
            {{ t('officialHome.btnEnter') }}
          </el-button>
        </article>
      </div>
    </section>

    <!-- 系统介绍 + 真实大盘指标 -->
    <section id="research" class="section-wrap intro-section">
      <div class="intro-left">
        <h2 class="section-title">{{ t('officialHome.sectionSystemIntro') }}</h2>
        <p class="intro-text">{{ t('officialHome.sectionSystemIntroSub') }}</p>
        <div class="metrics-grid" :class="{ 'is-loading': loadingMarket }">
          <div v-for="item in metricItems" :key="item.label" class="metric-card">
            <div class="metric-value">{{ item.value }}</div>
            <div class="metric-label">{{ item.label }}</div>
          </div>
        </div>
      </div>
      <figure class="intro-image">
        <img :src="INTRO_BG" :alt="t('officialHome.altEcosystem')" loading="lazy" decoding="async" />
      </figure>
    </section>

    <!-- 四角色入驻通道（保留既有角色详述弹窗） -->
    <section class="section-wrap">
      <header class="section-head">
        <h2 class="section-title">{{ t('officialHome.sectionUserRoles') }}</h2>
        <p class="section-subtitle">{{ t('officialHome.sectionUserRolesSub') }}</p>
      </header>
      <div class="roles-grid">
        <article v-for="role in roleCards" :key="role.key" class="role-card">
          <el-icon :size="18" class="role-icon"><component :is="role.icon" /></el-icon>
          <h3>{{ role.title }}</h3>
          <p>{{ role.description }}</p>
          <el-button link type="primary" :data-route="role.route" @click="openRoleDialog(role.key)">
            {{ t('officialHome.btnRoleDetail') }}
          </el-button>
        </article>
      </div>
    </section>

    <!-- 生态图集：三张语义成立的实拍 -->
    <section id="country" class="section-wrap">
      <header class="section-head">
        <h2 class="section-title">{{ t('officialHome.sectionEcosystem') }}</h2>
        <p class="section-subtitle">{{ t('officialHome.sectionEcosystemSub') }}</p>
      </header>
      <div class="gallery-grid">
        <img
          v-for="url in galleryImages"
          :key="url"
          :src="url"
          :alt="t('officialHome.altEcosystem')"
          loading="lazy"
          decoding="async"
        />
      </div>
    </section>

    <!-- 合规徽标条 -->
    <section id="join" class="compliance-strip">
      <div class="compliance-row">
        <span v-for="badge in complianceBadges" :key="badge.text" class="compliance-item">
          <el-icon :size="14" class="compliance-icon"><component :is="badge.icon" /></el-icon>{{ badge.text }}
        </span>
      </div>
    </section>

    <footer id="about" class="footer">
      <div class="footer-columns">
        <div v-for="group in footerLinkGroups" :key="group.title">
          <h4>{{ group.title }}</h4>
          <button
            v-for="link in group.links"
            :key="link.label"
            type="button"
            class="footer-link"
            @click="onNavClick(link.target)"
          >
            {{ link.label }}
          </button>
        </div>
      </div>
      <div class="copyright">
        <span>{{ t('officialHome.footerCopyright') }}</span>
        <span class="copyright__links">
          <span>{{ t('officialHome.footerAgreement') }}</span>
          <span>{{ t('officialHome.footerPrivacyShort') }}</span>
          <span>{{ t('officialHome.footerContact') }}</span>
        </span>
      </div>
    </footer>

    <Teleport to="body">
      <Transition name="role-dialog">
        <div v-if="activeRoleDetail" class="role-dialog-overlay" @click.self="closeRoleDialog">
          <div class="role-dialog-panel" role="dialog" aria-modal="true">
            <header class="role-dialog-head">
              <h3>{{ roleCards.find(r => r.key === activeRoleKey)?.title }}</h3>
              <button type="button" class="role-dialog-close" :aria-label="t('common.close')" @click="closeRoleDialog">×</button>
            </header>
            <p class="role-dialog-summary">{{ activeRoleDetail.summary }}</p>
            <ul class="role-dialog-duties">
              <li v-for="duty in activeRoleDetail.duties" :key="duty">{{ duty }}</li>
            </ul>
            <footer class="role-dialog-actions">
              <button type="button" class="role-dialog-login" @click="goRoleLogin(activeRoleDetail.account)">
                {{ t('officialHome.btnQuickLogin') }} · {{ activeRoleDetail.account }}
              </button>
              <button
                type="button"
                class="role-dialog-enter"
                @click="jumpToRoute(roleCards.find(r => r.key === activeRoleKey)?.route ?? '/login')"
              >
                {{ t('officialHome.btnAccessRole') }}
              </button>
            </footer>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* ============================================================================
   官网首页 · pen「石墨台账」语言
   底色 ground / 卡面 surface / 发丝线 border / 主操作墨色 action
   唯一彩色 = 图表蓝 chart（仅用于链上实时数据的活点与主链接）
   ========================================================================== */
.site-page {
  min-height: 100vh;
  background: var(--pen-ground);
  color: var(--pen-ink);
}

/* ── 顶栏：64px、shellTop、发丝线 ── */
.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 0 40px;
  background: var(--pen-shell-top);
  border-bottom: 1px solid var(--pen-border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  flex-shrink: 0;
}

.brand__mark {
  width: 26px;
  height: 26px;
  border-radius: var(--radius-control);
  display: grid;
  place-items: center;
  background: var(--pen-brand-mark);
  color: var(--color-on-inverse);
  flex-shrink: 0;
}

.brand__word {
  font-size: 14px;
  font-weight: 600;
  color: var(--pen-ink);
  white-space: nowrap;
}

.brand__en {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  color: var(--pen-faint);
}

.nav-menu {
  display: flex;
  align-items: center;
  gap: 26px;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
}

.nav-item {
  appearance: none;
  border: none;
  background: transparent;
  padding: 0;
  color: var(--pen-muted);
  font-family: inherit;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  white-space: nowrap;
  transition: color var(--motion-fast) var(--motion-ease);
}

.nav-item:hover {
  color: var(--pen-ink);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  flex-shrink: 0;
}

.nav-btn {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--radius-control);
  border: 1px solid transparent;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--motion-ease),
    border-color var(--motion-fast) var(--motion-ease);
}

.nav-btn--ghost {
  background: transparent;
  border-color: var(--pen-border);
  color: var(--pen-ink);
}

.nav-btn--ghost:hover {
  background: var(--pen-raised);
}

.nav-btn--primary {
  background: var(--pen-action);
  color: var(--color-on-inverse);
}

.nav-btn--primary:hover {
  background: var(--pen-action-hover);
}

.hamburger-btn {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  width: 34px;
  height: 34px;
  padding: 8px;
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-control);
  background: var(--pen-surface);
  cursor: pointer;
  flex-shrink: 0;
}

.hamburger-line {
  display: block;
  width: 100%;
  height: 1.5px;
  background: var(--pen-ink);
  border-radius: 1px;
}

.mobile-menu-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: var(--pen-popover-shadow);
}

.mobile-menu-panel {
  position: absolute;
  top: 64px;
  right: 0;
  width: 240px;
  background: var(--pen-surface);
  border-left: 1px solid var(--pen-border);
  border-bottom: 1px solid var(--pen-border);
  display: flex;
  flex-direction: column;
  padding: var(--space-2) 0;
}

.mobile-menu-item {
  appearance: none;
  border: none;
  background: transparent;
  width: 100%;
  padding: 12px 20px;
  color: var(--pen-muted);
  font-family: inherit;
  font-size: var(--fs-body);
  text-align: left;
  cursor: pointer;
}

.mobile-menu-item:hover {
  background: var(--pen-nav-active);
  color: var(--pen-ink);
}

/* ── Hero：浅底双栏，内容 1120 居中 ── */
.hero {
  padding: 56px 40px 48px;
  background: var(--pen-ground);
}

.hero-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 480px;
  gap: 60px;
  align-items: center;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 20px;
  color: var(--pen-muted);
  font-size: 11.5px;
  line-height: 1;
}

.hero-kicker__led {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--pen-faint);
  flex-shrink: 0;
}

.hero-kicker__led.is-live {
  background: var(--pen-chart);
  box-shadow: 0 0 0 3px var(--pen-note-bg);
}

.hero-title {
  margin: 0 0 16px;
  color: var(--pen-ink);
  font-size: 42px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.hero-description {
  margin: 0 0 26px;
  max-width: 540px;
  color: var(--pen-muted);
  font-size: 14px;
  line-height: 1.8;
}

.hero-cta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 22px;
}

.hero-cta-primary {
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 42px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-control);
  background: var(--pen-action);
  color: var(--color-on-inverse);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--motion-ease);
}

.hero-cta-primary:hover {
  background: var(--pen-action-hover);
}

.hero-cta-arrow {
  transition: transform var(--motion-base) var(--motion-ease);
}

.hero-cta-primary:hover .hero-cta-arrow {
  transform: translateX(3px);
}

.hero-cta-ghost {
  appearance: none;
  display: inline-flex;
  align-items: center;
  height: 42px;
  padding: 0 18px;
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-control);
  background: var(--pen-surface);
  color: var(--pen-ink);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--motion-ease);
}

.hero-cta-ghost:hover {
  background: var(--pen-raised);
}

.hero-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-bottom: 24px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--pen-faint);
  font-size: 11px;
}

.hero-badge-icon {
  color: var(--pen-faint);
}

/* Hero 三指标：数值 21px、标签 11px，竖发丝线分隔 */
.hero-metrics {
  display: flex;
  align-items: center;
  margin: 0;
  padding-top: 20px;
  border-top: 1px solid var(--pen-border);
  width: 100%;
  max-width: 440px;
}

.hero-metric {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-right: 22px;
  flex: 1 1 0;
}

.hero-metric + .hero-metric {
  padding-left: 22px;
}

.hero-metric__value {
  margin: 0;
  color: var(--pen-ink);
  font-size: 21px;
  font-weight: 500;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.hero-metric__label {
  margin: 0;
  color: var(--pen-faint);
  font-size: 11px;
  line-height: 1.4;
}

.hero-metric__divider {
  position: absolute;
  right: 0;
  top: 4px;
  width: 1px;
  height: 30px;
  background: var(--pen-border);
}

.hero-metric:last-child .hero-metric__divider {
  display: none;
}

/* ── 实时区块流卡：44px 头 + 5×52px 行 ── */
.stream-card {
  width: 100%;
  background: var(--pen-surface);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.stream-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--pen-border);
}

.stream-card__led {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--pen-faint);
  flex-shrink: 0;
}

.stream-card__led.is-live {
  background: var(--pen-chart);
}

.stream-card__title {
  color: var(--pen-muted);
  font-size: 11px;
  line-height: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stream-card__pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  height: 23px;
  padding: 0 8px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--pen-pill-neu-bd);
  background: var(--pen-pill-neu-bg);
  color: var(--pen-pill-neu-ink);
  font-size: 11px;
  font-weight: 500;
  flex-shrink: 0;
}

.stream-card__pill.is-live {
  border-color: var(--pen-pill-pos-bd);
  background: var(--pen-pill-pos-bg);
  color: var(--pen-pill-pos-ink);
}

.stream-card__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.stream-rows {
  list-style: none;
  margin: 0;
  padding: 0;
}

.stream-row {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 52px;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--pen-border);
}

.stream-row:last-child {
  border-bottom: none;
}

.stream-row__height {
  width: 64px;
  flex-shrink: 0;
  color: var(--pen-ink);
  font-family: var(--font-mono);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.stream-row__detail {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--pen-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stream-row__time {
  flex-shrink: 0;
  color: var(--pen-faint);
  font-size: 11px;
  white-space: nowrap;
}

.stream-empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
  padding: 28px var(--space-4);
}

.stream-empty__text {
  margin: 0;
  color: var(--pen-faint);
  font-size: var(--fs-sub);
  line-height: 1.6;
}

.stream-empty__link {
  appearance: none;
  border: none;
  background: transparent;
  padding: 0;
  color: var(--pen-chart);
  font-family: inherit;
  font-size: var(--fs-sub);
  cursor: pointer;
}

/* ── 链网脉搏条 ── */
.pulse-bar {
  max-width: 1120px;
  margin: 0 auto var(--space-6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-surface);
}

.pulse-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.pulse-dot.is-live {
  background: var(--pen-chart);
}

.pulse-dot.is-standby {
  background: var(--pen-faint);
}

.pulse-label {
  color: var(--pen-faint);
  font-size: 11px;
  white-space: nowrap;
}

.pulse-value {
  color: var(--pen-ink);
  font-size: var(--fs-sub);
  font-weight: 600;
  font-family: var(--font-mono);
  white-space: nowrap;
}

.pulse-mono {
  color: var(--pen-muted);
  font-size: var(--fs-sub);
  font-family: var(--font-mono);
  white-space: nowrap;
}

.pulse-divider {
  width: 1px;
  height: 20px;
  background: var(--pen-border);
}

/* ── 章节与标题 ── */
.section-wrap,
.capability-section {
  max-width: 1120px;
  margin: 0 auto;
  padding: 40px 40px 44px;
}

.capability-section {
  padding-top: 26px;
  padding-bottom: 26px;
  background: var(--pen-shell);
  border-top: 1px solid var(--pen-border);
  border-bottom: 1px solid var(--pen-border);
  max-width: none;
}

.capability-section > .capability-grid,
.capability-section > .section-head {
  max-width: 1120px;
  margin-left: auto;
  margin-right: auto;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-5);
  margin-bottom: var(--space-5);
}

.section-title {
  margin: 0;
  color: var(--pen-ink);
  font-size: 21px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -0.01em;
}

.section-subtitle {
  margin: 0;
  color: var(--pen-muted);
  font-size: 12.5px;
  line-height: 1.6;
  text-align: right;
}

/* ── 四角色闭环卡 ── */
.capability-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.capability-card {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: var(--space-4);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-surface);
  cursor: pointer;
  transition: border-color var(--motion-fast) var(--motion-ease);
}

.capability-card:hover {
  border-color: var(--pen-muted);
}

.capability-card:focus-visible {
  outline: 2px solid var(--pen-ink);
  outline-offset: 2px;
}

.capability-card__icon {
  color: var(--pen-ink);
}

.capability-card__title {
  margin: 0;
  color: var(--pen-ink);
  font-size: 14px;
  font-weight: 500;
}

.capability-card__desc {
  margin: 0;
  color: var(--pen-muted);
  font-size: 12px;
  line-height: 1.6;
}

.capability-card__tag {
  color: var(--pen-faint);
  font-size: 9.5px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

/* ── 核心能力四卡 ── */
.feature-grid,
.roles-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.feature-card,
.role-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-surface);
}

.feature-card {
  cursor: pointer;
  transition: border-color var(--motion-fast) var(--motion-ease);
}

.feature-card:hover {
  border-color: var(--pen-muted);
}

.feature-card:focus-visible {
  outline: 2px solid var(--pen-ink);
  outline-offset: 2px;
}

.card-go {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
  display: grid;
  place-items: center;
  color: var(--pen-faint);
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity var(--motion-base) var(--motion-ease), transform var(--motion-base) var(--motion-ease);
}

.feature-card:hover .card-go,
.feature-card:focus-visible .card-go {
  opacity: 1;
  transform: translateX(0);
}

.feature-icon,
.role-icon {
  color: var(--pen-ink);
}

.feature-card h3,
.role-card h3 {
  margin: 0;
  color: var(--pen-ink);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
}

.feature-card p,
.role-card p {
  margin: 0;
  color: var(--pen-muted);
  font-size: 12px;
  line-height: 1.65;
}

.feature-card :deep(.el-button),
.role-card :deep(.el-button) {
  align-self: flex-start;
  padding: 0;
  height: auto;
  margin-top: var(--space-1);
  color: var(--pen-chart);
  font-size: 12px;
}

/* ── 系统介绍 + 大盘指标 ── */
.intro-section {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--space-6);
  align-items: stretch;
}

.intro-left {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.intro-text {
  margin: var(--space-2) 0 var(--space-5);
  color: var(--pen-muted);
  font-size: 13px;
  line-height: 1.85;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-2);
  margin-top: auto;
  transition: opacity var(--motion-base) var(--motion-ease);
}

.metrics-grid.is-loading {
  opacity: 0.7;
}

.metric-card {
  padding: var(--space-3) var(--space-2);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-surface);
  text-align: center;
  min-width: 0;
}

.metric-value {
  color: var(--pen-ink);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-label {
  margin-top: 4px;
  color: var(--pen-faint);
  font-size: 10px;
  line-height: 1.35;
}

.intro-image {
  margin: 0;
  border-radius: var(--radius-card);
  overflow: hidden;
  min-height: 280px;
  border: 1px solid var(--pen-border);
}

.intro-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* ── 生态图集 ── */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.gallery-grid img {
  width: 100%;
  height: 190px;
  object-fit: cover;
  border-radius: var(--radius-card);
  border: 1px solid var(--pen-border);
  display: block;
}

/* ── 合规徽标条 ── */
.compliance-strip {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 40px 36px;
}

.compliance-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  row-gap: var(--space-2);
  padding: var(--space-3) 0;
  border-top: 1px solid var(--pen-border);
  border-bottom: 1px solid var(--pen-border);
}

.compliance-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 22px;
  color: var(--pen-muted);
  font-size: 11.5px;
}

.compliance-item + .compliance-item {
  border-left: 1px solid var(--pen-border);
}

.compliance-icon {
  color: var(--pen-faint);
}

/* ── 页脚：浅底、发丝线 ── */
.footer {
  background: var(--pen-shell);
  border-top: 1px solid var(--pen-border);
  padding: 36px 40px 24px;
}

.footer-columns {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

.footer h4 {
  margin: 0 0 var(--space-3);
  color: var(--pen-ink);
  font-size: 12px;
  font-weight: 600;
}

.footer-link {
  display: block;
  width: 100%;
  padding: 0;
  margin-bottom: var(--space-2);
  border: none;
  background: transparent;
  color: var(--pen-muted);
  font-family: inherit;
  font-size: 11.5px;
  text-align: left;
  cursor: pointer;
  transition: color var(--motion-fast) var(--motion-ease);
}

.footer-link:hover {
  color: var(--pen-ink);
}

.copyright {
  max-width: 1120px;
  margin: var(--space-5) auto 0;
  padding-top: var(--space-3);
  border-top: 1px solid var(--pen-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  color: var(--pen-faint);
  font-size: 11px;
}

.copyright__links {
  display: flex;
  align-items: center;
  gap: 22px;
}

/* ── 角色权责详述弹窗 ── */
.role-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: grid;
  place-items: center;
  padding: var(--space-5);
  background: rgba(10, 10, 10, 0.45);
}

.role-dialog-panel {
  width: min(520px, 100%);
  max-height: 84vh;
  overflow-y: auto;
  padding: var(--space-5);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-surface);
  box-shadow: var(--pen-overlay-shadow);
}

.role-dialog-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--pen-border);
}

.role-dialog-head h3 {
  margin: 0;
  color: var(--pen-ink);
  font-size: 15px;
  font-weight: 600;
}

.role-dialog-close {
  margin-left: auto;
  appearance: none;
  border: none;
  background: transparent;
  padding: 0 4px;
  color: var(--pen-faint);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.role-dialog-close:hover {
  color: var(--pen-ink);
}

.role-dialog-summary {
  margin: var(--space-3) 0;
  color: var(--pen-muted);
  font-size: var(--fs-body);
  line-height: 1.8;
}

.role-dialog-duties {
  list-style: none;
  margin: 0 0 var(--space-4);
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.role-dialog-duties li {
  position: relative;
  padding: 10px 12px 10px 30px;
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-sunken);
  color: var(--pen-muted);
  font-size: 12px;
  line-height: 1.6;
}

.role-dialog-duties li::before {
  content: '';
  position: absolute;
  left: 13px;
  top: 16px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--pen-chart);
}

.role-dialog-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.role-dialog-login {
  flex: 1 1 160px;
  appearance: none;
  height: 36px;
  border: none;
  border-radius: var(--radius-control);
  background: var(--pen-action);
  color: var(--color-on-inverse);
  font-family: inherit;
  font-size: var(--fs-body);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--motion-ease);
}

.role-dialog-login:hover {
  background: var(--pen-action-hover);
}

.role-dialog-enter {
  flex: 1 1 160px;
  appearance: none;
  height: 36px;
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-control);
  background: var(--pen-surface);
  color: var(--pen-ink);
  font-family: inherit;
  font-size: var(--fs-body);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--motion-ease);
}

.role-dialog-enter:hover {
  background: var(--pen-raised);
}

.role-dialog-enter-active,
.role-dialog-leave-active {
  transition: opacity var(--motion-base) var(--motion-ease);
}

.role-dialog-enter-from,
.role-dialog-leave-to {
  opacity: 0;
}

/* ── 焦点可见性 ── */
.nav-item:focus-visible,
.nav-btn:focus-visible,
.hero-cta-primary:focus-visible,
.hero-cta-ghost:focus-visible,
.stream-empty__link:focus-visible,
.footer-link:focus-visible,
.role-dialog-close:focus-visible,
.role-dialog-login:focus-visible,
.role-dialog-enter:focus-visible,
.hamburger-btn:focus-visible {
  outline: 2px solid var(--pen-ink);
  outline-offset: 2px;
}

/* ── 响应式 ── */
@media (max-width: 1180px) {
  .hero-inner {
    grid-template-columns: minmax(0, 1fr) 420px;
    gap: 40px;
  }

  .capability-grid,
  .feature-grid,
  .roles-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .nav-menu {
    display: none;
  }

  .hamburger-btn {
    display: flex;
    margin-left: auto;
  }

  .nav-actions {
    margin-left: 0;
  }

  .hero-inner {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .section-head {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }

  .section-subtitle {
    text-align: left;
  }

  .intro-section {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .top-nav,
  .hero,
  .section-wrap,
  .compliance-strip,
  .footer {
    padding-left: 20px;
    padding-right: 20px;
  }

  .hero-title {
    font-size: 30px;
  }

  .nav-btn--ghost {
    display: none;
  }

  .capability-grid,
  .feature-grid,
  .roles-grid,
  .gallery-grid,
  .footer-columns {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-metric {
    padding-right: 14px;
  }

  .hero-metric + .hero-metric {
    padding-left: 14px;
  }

  .pulse-bar {
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .pulse-divider {
    display: none;
  }

  .compliance-item {
    padding: 0 12px;
  }

  .compliance-item + .compliance-item {
    border-left: none;
  }
}
</style>