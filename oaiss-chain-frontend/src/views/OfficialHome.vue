<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：公开门户 - OAISS CHAIN 平台官方门户大厅
 * 【路由地址】：/official-home
 * 【访问权限】：公开访问 (无需登录)
 * 【界面可见功能】：
 *   1. 顶部导航与英雄展区：系统核心定位与愿景展示
 *   2. 联盟链底层状态看板：实时轮询 Hyperledger Fabric 节点运行状态与区块高度
 *   3. 四大核心特性导航：碳核算填报、双向连续拍卖、区块链不可篡改存证、AI智能预测
 *   4. 四角色协同入驻通道：控排企业、核查机构、监管部门、平台管理入口
 *   5. 国家“双碳”战略宣贯与行业研究动态
 * 【对应后端 Controller & API】：
 *   - BlockchainController:
 *     * GET /api/v1/blockchain/status -> getStatus() 轮询底层区块链健康度
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
  Message,
  Monitor,
  OfficeBuilding,
  SetUp,
  Share,
  Stamp,
} from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { HERO_BG, INTRO_BG, GALLERY_IMAGES } from '../config/images'
import { getStatus } from '../api/blockchain'
import { getMarketOverview } from '../api/search'
import type { MarketOverview } from '../types'
import { getAccessToken } from '../utils/auth'

const { t } = useI18n()

const router = useRouter()
const route = useRoute()

const mobileMenuOpen = ref(false)

const navItems = computed(() => [
  { key: 'hero', label: 'OAISS' },
  { key: 'build', label: t('officialHome.navBuild') },
  { key: 'join', label: t('officialHome.navParticipate') },
  { key: 'research', label: t('officialHome.navResearch') },
  { key: 'platform', label: t('officialHome.navPlatform') },
  { key: 'country', label: t('officialHome.navCountry') },
  { key: 'about', label: t('officialHome.navAbout') },
])

const heroBadges = computed(() => [
  { icon: Document, text: t('officialHome.badgeStandard') },
  { icon: Link, text: t('officialHome.badgeFabric') },
  { icon: Lock, text: t('officialHome.badgeCrypto') },
])

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

const roleCards = computed(() => [
  {
    key: 'enterprise',
    title: t('officialHome.roleEnterprise'),
    description: t('officialHome.roleEnterpriseDesc'),
    icon: OfficeBuilding,
    route: '/enterprise/carbon/upload',
    accent: 'var(--color-accent)',
  },
  {
    key: 'admin',
    title: t('officialHome.roleAdmin'),
    description: t('officialHome.roleAdminDesc'),
    icon: SetUp,
    route: '/admin/system/users',
    accent: '#a78bfa',
  },
  {
    key: 'auditor',
    title: t('officialHome.roleAuditor'),
    description: t('officialHome.roleAuditorDesc'),
    icon: CircleCheck,
    route: '/auditor/audit/list',
    accent: '#60a5fa',
  },
  {
    key: 'thirdParty',
    title: t('officialHome.roleThirdParty'),
    description: t('officialHome.roleThirdPartyDesc'),
    icon: Monitor,
    route: '/third-party/monitor',
    accent: '#2dd4bf',
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

const openRoleDialog = (key: string) => {
  activeRoleKey.value = key
}

const closeRoleDialog = () => {
  activeRoleKey.value = ''
}

const goRoleLogin = (account: string) => {
  activeRoleKey.value = ''
  router.push({ path: '/login', query: { account } })
}

const complianceBadges = computed(() => [
  { icon: Stamp, text: t('officialHome.badgeStandardFull') },
  { icon: Link, text: t('officialHome.badgeFabricFull') },
  { icon: Lock, text: t('officialHome.badgeCryptoFull') },
  { icon: Medal, text: t('officialHome.badgeIsoFull') },
])

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

/* ── 市场大盘宏观指标响应式状态与优雅降级配置 ── */
const marketOverview = ref<MarketOverview | null>(null)
const loadingMarket = ref(false)

const formatNumber = (val: number | string | null | undefined, fallback = '--'): string => {
  if (val === null || val === undefined || val === '') return fallback
  const num = Number(val)
  if (Number.isNaN(num)) return String(val)
  return num.toLocaleString('en-US')
}

// 优雅降级默认指标 (与原静态兜底保持风格一致，千分位展示)
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
  const availabilityLatency = `${availability} / ${latency}`

  return [
    { label: t('officialHome.metricEnterprises'), value: enterprises },
    { label: t('officialHome.metricCarbonReports'), value: reports },
    { label: t('officialHome.metricTransactions'), value: transactions },
    { label: t('officialHome.metricTradingVolume'), value: volume },
    { label: t('officialHome.metricAvailabilityLatency'), value: availabilityLatency },
  ]
})

const fetchMarketOverview = async () => {
  loadingMarket.value = true
  try {
    const data = await getMarketOverview()
    if (data) {
      marketOverview.value = data
      // 若链网脉搏未处于 live 状态，可使用大盘返回的链网宏观状态进行同步展示
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

const galleryImages = GALLERY_IMAGES

// =========================================================================
// [DEFENSE-TRACE-OFFICIAL-HOME-01]: 门户大厅主界面与底层联盟链脉搏探针
// 【业务场景】：系统官方首页门户、多角色导航入口与 Hyperledger Fabric 状态感知
// 【对应API】：api/blockchain.ts -> getStatus()
// 【后端映射】：GET /api/v1/blockchain/status -> BlockchainController.getStatus()
// =========================================================================
/* ── 链网脉搏条：登录态下拉真实 Fabric 状态，访客态静默展示环境提示 ── */
const pulse = ref({
  online: false,
  mode: 'STANDBY',
  channel: 'mychannel',
  blockHeight: null as number | null,
  txCount: null as number | null,
  chaincode: 'carbon-chaincode',
})

const pulseNetworkMode = computed(() => {
  if (!pulse.value.online) {
    return t('officialHome.pulseStandbyMode')
  }
  const mode = (pulse.value.mode && pulse.value.mode !== 'STANDBY' ? pulse.value.mode : 'FABRIC').toUpperCase()
  return `${mode} · ONLINE`
})

const refreshPulse = async () => {
  if (!getAccessToken()) return
  try {
    const status = await getStatus()
    pulse.value = {
      online: Boolean(status.connected),
      mode: String(status.mode ?? 'FABRIC'),
      channel: String(status.channel ?? 'mychannel'),
      blockHeight: null,
      txCount: null,
      chaincode: String(status.chaincode ?? 'carbon-chaincode'),
    }
  } catch {
    /* 静默失败：脉搏条回退为环境提示态，不打扰访客 */
  }
}

/* ── Hero 存证数字孪生视窗：四步流转 + 数据指纹动画 ── */
const twinSteps = computed(() => [
  { icon: Document, label: t('officialHome.twinStep1'), desc: t('officialHome.twinStep1Desc') },
  { icon: EditPen, label: t('officialHome.twinStep2'), desc: t('officialHome.twinStep2Desc') },
  { icon: Connection, label: t('officialHome.twinStep3'), desc: t('officialHome.twinStep3Desc') },
  { icon: CircleCheck, label: t('officialHome.twinStep4'), desc: t('officialHome.twinStep4Desc') },
])

const activeStep = ref(0)
const fingerprint = ref('0x17d629d54dcd8c3d48a80a7301b9c58d85980a25266c679fd1aaee7b5a7d209f')

const HEX_CHARS = '0123456789abcdef'
const HEX_SLICE_START = 2
const HEX_SLICE_END = 10

function scrambleFingerprint() {
  const chars = fingerprint.value.split('')
  for (let i = HEX_SLICE_START; i < HEX_SLICE_END; i += 1) {
    chars[i] = HEX_CHARS[Math.floor(Math.random() * HEX_CHARS.length)]
  }
  fingerprint.value = chars.join('')
}

let stepTimer: number | undefined
let scrambleTimer: number | undefined

onMounted(() => {
  void fetchMarketOverview()
  void refreshPulse()
  stepTimer = window.setInterval(() => {
    activeStep.value = (activeStep.value + 1) % twinSteps.value.length
  }, 2200)
  scrambleTimer = window.setInterval(scrambleFingerprint, 450)
})

onBeforeUnmount(() => {
  window.clearInterval(stepTimer)
  window.clearInterval(scrambleTimer)
})

const scrollToSection = async (key: string) => {
  await nextTick()
  const el = document.getElementById(key)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const onNavClick = (key: string) => {
  void scrollToSection(key)
  mobileMenuOpen.value = false
}

const jumpToRoute = (path: string) => {
  router.push(path)
}

const onSocialClick = () => {
  ElMessage.success(t('officialHome.socialTriggered'))
}

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
    <header class="top-nav">
      <div class="brand" @click="onNavClick('hero')">OAISS</div>
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
      <button class="hamburger-btn" type="button" @click="mobileMenuOpen = !mobileMenuOpen">
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
      <div class="social-links">
        <button type="button" class="social-btn" @click="onSocialClick"><el-icon><Message /></el-icon></button>
        <button type="button" class="social-btn" @click="onSocialClick"><el-icon><Share /></el-icon></button>
        <button type="button" class="social-btn" @click="onSocialClick"><el-icon><Link /></el-icon></button>
      </div>
      <div class="nav-cta-group">
        <button type="button" class="nav-cta-login" @click="jumpToRoute('/login')">
          {{ t('officialHome.btnLogin') }}
        </button>
        <button type="button" class="nav-cta-primary" @click="onNavClick('platform')">
          {{ t('officialHome.btnQuickExperience') }}
        </button>
      </div>
    </header>

    <section id="hero" class="hero" :style="{ backgroundImage: `linear-gradient(115deg, rgba(6, 34, 39, 0.92) 0%, rgba(9, 47, 48, 0.78) 46%, rgba(10, 58, 55, 0.45) 100%), url(${HERO_BG})` }">
      <div class="hero-overlay">
        <div class="hero-inner">
          <div class="hero-copy">
            <div class="hero-badges">
              <span v-for="badge in heroBadges" :key="badge.text" class="hero-badge">
                <el-icon :size="14" class="hero-badge-icon"><component :is="badge.icon" /></el-icon>{{ badge.text }}
              </span>
            </div>
            <h1 class="hero-title">{{ t('officialHome.heroTitle') }}</h1>
            <p class="hero-description">
              {{ t('officialHome.heroSubtitle') }}
            </p>
            <div class="hero-cta-row">
              <button type="button" class="hero-cta-primary" @click="onNavClick('platform')">
                {{ t('officialHome.btnExperienceRoles') }}
                <span class="hero-cta-arrow">→</span>
              </button>
              <button type="button" class="hero-cta-ghost" @click="onNavClick('build')">
                {{ t('officialHome.btnInspectLedger') }}
              </button>
            </div>
          </div>
          <div class="hero-visual" aria-hidden="true">
            <div class="twin-card">
              <div class="twin-head">
                <span class="twin-status-dot" :class="pulse.online ? 'is-live' : 'is-standby'" />
                <span class="twin-head-title">{{ t('officialHome.twinWindowTitle') }}</span>
                <span class="twin-head-live">{{ pulse.online ? t('officialHome.twinLive') : t('officialHome.twinStandby') }}</span>
              </div>
              <ol class="twin-steps">
                <li
                  v-for="(step, idx) in twinSteps"
                  :key="step.label"
                  class="twin-step"
                  :class="{ 'is-active': activeStep === idx, 'is-done': activeStep > idx }"
                >
                  <span class="twin-step-icon"><el-icon :size="16"><component :is="step.icon" /></el-icon></span>
                  <span class="twin-step-text">
                    <strong>{{ step.label }}</strong>
                    <em>{{ step.desc }}</em>
                  </span>
                </li>
              </ol>
              <div class="twin-fingerprint">
                <span class="twin-fingerprint-label">{{ t('officialHome.twinFingerprintLabel') }}</span>
                <span class="twin-fingerprint-hash">{{ fingerprint }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section id="pulse" class="pulse-bar" aria-label="blockchain live status">
      <div class="pulse-item">
        <span class="pulse-dot" :class="pulse.online ? 'is-live' : 'is-standby'" />
        <span class="pulse-label">{{ t('officialHome.pulseNetwork') }}</span>
        <span class="pulse-value">{{ pulseNetworkMode }}</span>
      </div>
      <div class="pulse-divider" />
      <div class="pulse-item">
        <span class="pulse-label">{{ t('officialHome.pulseChannel') }}</span>
        <span class="pulse-value">{{ pulse.channel }}</span>
      </div>
      <div class="pulse-divider" />
      <div class="pulse-item">
        <span class="pulse-label">{{ t('officialHome.pulseChaincode') }}</span>
        <span class="pulse-value">{{ pulse.chaincode }}</span>
      </div>
      <div class="pulse-divider" />
      <div class="pulse-item">
        <span class="pulse-label">{{ t('officialHome.pulseCrypto') }}</span>
        <span class="pulse-value">RSA-2048 · AES-256-GCM</span>
      </div>
    </section>

    <section id="build" class="section-wrap">
      <div class="section-title-wrap">
        <h2 class="section-title">{{ t('officialHome.sectionCoreFeatures') }}</h2>
        <p class="section-subtitle">{{ t('officialHome.sectionCoreFeaturesSub') }}</p>
      </div>
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
          <span class="card-go" aria-hidden="true"><el-icon :size="16"><ArrowRight /></el-icon></span>
          <div class="feature-icon"><el-icon><component :is="item.icon" /></el-icon></div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
        </article>
      </div>
    </section>

    <section id="research" class="section-wrap intro-section">
      <div class="intro-left">
        <h2 class="section-title">{{ t('officialHome.sectionSystemIntro') }}</h2>
        <p class="intro-text">
          {{ t('officialHome.sectionSystemIntroSub') }}
        </p>
        <div class="metrics-grid" :class="{ 'is-loading': loadingMarket }">
          <div v-for="item in metricItems" :key="item.label" class="metric-card">
            <div class="metric-value">{{ item.value }}</div>
            <div class="metric-label">{{ item.label }}</div>
          </div>
        </div>
      </div>
      <div class="intro-image" :style="{ backgroundImage: `url(${INTRO_BG})` }" />
    </section>

    <section id="platform" class="section-wrap">
      <div class="section-title-wrap">
        <h2 class="section-title">{{ t('officialHome.sectionUserRoles') }}</h2>
        <p class="section-subtitle">{{ t('officialHome.sectionUserRolesSub') }}</p>
      </div>
      <div class="roles-grid">
        <article v-for="role in roleCards" :key="role.key" class="role-card" :style="{ '--role-accent': role.accent }">
          <div class="role-icon"><el-icon><component :is="role.icon" /></el-icon></div>
          <h3>{{ role.title }}</h3>
          <p>{{ role.description }}</p>
          <button type="button" class="role-detail-btn" @click="openRoleDialog(role.key)">
            {{ t('officialHome.btnRoleDetail') }}<span class="role-detail-arrow">→</span>
          </button>
        </article>
      </div>
    </section>

    <section id="country" class="section-wrap">
      <div class="section-title-wrap">
        <h2 class="section-title">{{ t('officialHome.sectionEcosystem') }}</h2>
        <p class="section-subtitle">{{ t('officialHome.sectionEcosystemSub') }}</p>
      </div>
      <div class="gallery-grid">
        <img v-for="url in galleryImages" :key="url" :src="url" :alt="t('officialHome.altEcosystem')" />
      </div>
    </section>

    <section id="join" class="compliance-strip">
      <div class="compliance-row">
        <span v-for="badge in complianceBadges" :key="badge.text" class="compliance-item">
          <el-icon :size="15" class="compliance-icon"><component :is="badge.icon" /></el-icon>{{ badge.text }}
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
      <div class="copyright">© 2026 OAISS. All rights reserved.</div>
    </footer>

    <Teleport to="body">
      <Transition name="role-dialog">
        <div v-if="activeRoleDetail" class="role-dialog-overlay" @click.self="closeRoleDialog">
          <div
            class="role-dialog-panel"
            :style="{ '--role-accent': roleCards.find(r => r.key === activeRoleKey)?.accent ?? 'var(--color-accent)' }"
            role="dialog"
            aria-modal="true"
          >
            <header class="role-dialog-head">
              <span class="role-dialog-dot" />
              <h3>{{ roleCards.find(r => r.key === activeRoleKey)?.title }}</h3>
              <button type="button" class="role-dialog-close" aria-label="close" @click="closeRoleDialog">×</button>
            </header>
            <p class="role-dialog-summary">{{ activeRoleDetail.summary }}</p>
            <ul class="role-dialog-duties">
              <li v-for="duty in activeRoleDetail.duties" :key="duty">{{ duty }}</li>
            </ul>
            <footer class="role-dialog-actions">
              <button type="button" class="role-dialog-login" @click="goRoleLogin(activeRoleDetail.account)">
                {{ t('officialHome.btnQuickLogin') }} · {{ activeRoleDetail.account }}
              </button>
              <button type="button" class="role-dialog-enter" @click="jumpToRoute(roleCards.find(r => r.key === activeRoleKey)?.route ?? '/login')">
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
.site-page {
  min-height: 100vh;
  background: var(--color-on-inverse-muted);
  color: var(--color-brand-strong);
}

.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: rgba(11, 45, 48, 0.88);
  backdrop-filter: blur(8px);
}

.brand {
  color: var(--mint-100);
  font-weight: 700;
  font-size: var(--text-xl);
  cursor: pointer;
}

.nav-menu {
  display: flex;
  gap: 16px;
}

.nav-item {
  border: none;
  background: transparent;
  color: var(--mint-050);
  font-size: var(--text-base);
  cursor: pointer;
  padding: 6px 8px;
}

.nav-item:hover {
  color: var(--mint-300);
}

.social-links {
  display: flex;
  gap: 8px;
}

.social-btn {
  border: 1px solid rgba(189, 243, 230, 0.32);
  background: rgba(255, 255, 255, 0.08);
  color: var(--mint-200);
  width: 34px;
  height: 34px;
  border-radius: 8px;
  cursor: pointer;
}

.nav-cta-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-cta-login {
  border: 1px solid rgba(189, 243, 230, 0.36);
  background: transparent;
  color: var(--mint-100);
  padding: 8px 16px;
  border-radius: 10px;
  font-size: var(--text-base);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.nav-cta-login:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--color-on-inverse);
}

.nav-cta-primary {
  border: none;
  background: var(--brand-gradient);
  color: var(--text-on-dark);
  padding: 9px 18px;
  border-radius: 10px;
  font-weight: 600;
  font-size: var(--text-base);
  cursor: pointer;
  transition: transform 0.2s ease;
}

.nav-cta-primary:hover {
  transform: translateY(-1px);
}

.hamburger-btn {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(189, 243, 230, 0.32);
  background: rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  cursor: pointer;
  padding: 6px;
}

.hamburger-line {
  display: block;
  width: 100%;
  height: 2px;
  background: var(--mint-200);
  border-radius: 1px;
}

.mobile-menu-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.45);
}

.mobile-menu-panel {
  position: absolute;
  top: 0;
  right: 0;
  width: 240px;
  height: 100%;
  background: var(--color-brand-strong);
  padding: 68px 0 20px;
  display: flex;
  flex-direction: column;
}

.mobile-menu-item {
  display: block;
  width: 100%;
  padding: 14px 24px;
  border: none;
  background: transparent;
  color: var(--mint-050);
  font-size: 15px;
  text-align: left;
  cursor: pointer;
}

.mobile-menu-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--mint-300);
}

.hero {
  min-height: 72vh;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
}

.hero-overlay {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 64px 20px;
}

.hero-inner {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 40px;
  align-items: center;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
}

.hero-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 22px;
  margin-bottom: 18px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--mint-200);
  font-size: var(--text-sm);
}

.hero-badge .hero-badge-icon {
  color: var(--color-on-inverse);
}

.hero-title {
  margin: 0 0 12px;
  color: var(--color-on-inverse-muted);
  font-size: var(--text-display);
  font-weight: 700;
  line-height: 1.25;
}

.hero-description {
  margin: 0 0 26px;
  color: var(--color-on-inverse-muted);
  font-size: var(--text-md);
  max-width: 560px;
  line-height: 1.8;
}

.hero-cta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.hero-cta-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: var(--brand-gradient);
  color: var(--text-on-dark);
  font-size: var(--text-md);
  font-weight: 600;
  padding: 13px 26px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.hero-cta-primary:hover {
  transform: translateY(-2px);
}

.hero-cta-arrow {
  transition: transform 0.2s ease;
}

.hero-cta-primary:hover .hero-cta-arrow {
  transform: translateX(3px);
}

.hero-cta-ghost {
  border: 1px solid rgba(202, 255, 241, 0.5);
  background: rgba(10, 58, 55, 0.35);
  backdrop-filter: blur(6px);
  color: var(--mint-100);
  font-size: var(--text-md);
  padding: 13px 26px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.hero-cta-ghost:hover {
  background: rgba(20, 84, 78, 0.5);
  border-color: rgba(202, 255, 241, 0.8);
}

/* ── Hero 存证数字孪生视窗 ── */
.hero-visual {
  display: flex;
  justify-content: center;
}

.twin-card {
  width: min(400px, 100%);
  border-radius: 18px;
  border: 1px solid rgba(198, 250, 235, 0.22);
  background: var(--color-brand-strong);
  backdrop-filter: blur(14px);
  padding: 18px 18px 16px;
  color: var(--mint-100);
}

.twin-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(198, 250, 235, 0.14);
}

.twin-status-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.twin-status-dot.is-live {
  background: var(--color-accent);
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.twin-status-dot.is-standby {
  background: var(--color-text-muted);
  outline: 2px solid var(--color-border);
  outline-offset: 2px;
}

.twin-head-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-on-inverse-muted);
}

.twin-head-live {
  margin-left: auto;
  font-size: var(--text-xs);
  color: var(--mint-300);
  letter-spacing: 0.04em;
}

.twin-steps {
  list-style: none;
  margin: 14px 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.twin-step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.04);
  opacity: 0.6;
  transition: opacity 0.35s ease, border-color 0.35s ease, background 0.35s ease, transform 0.35s ease;
}

.twin-step.is-active {
  opacity: 1;
  border-color: rgba(94, 234, 212, 0.45);
  background: rgba(45, 212, 191, 0.12);
  transform: translateX(4px);
}

.twin-step.is-done {
  opacity: 0.85;
  border-color: rgba(94, 234, 212, 0.2);
}

.twin-step-icon {
  display: grid;
  place-items: center;
  color: var(--color-on-inverse-muted);
}

.twin-step-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.twin-step-text strong {
  font-size: var(--text-sm);
  color: var(--color-on-inverse-muted);
  font-weight: 600;
}

.twin-step-text em {
  font-style: normal;
  font-size: var(--text-xs);
  color: var(--color-on-inverse-muted);
}

.twin-fingerprint {
  border-top: 1px solid rgba(198, 250, 235, 0.14);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.twin-fingerprint-label {
  font-size: var(--text-xs);
  color: var(--color-on-inverse-muted);
}

.twin-fingerprint-hash {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: var(--text-xs);
  letter-spacing: 0.02em;
  color: var(--color-on-inverse);
  word-break: break-all;
}

/* ── 全局链网脉搏条 ── */
.pulse-bar {
  max-width: 1200px;
  margin: -26px auto 0;
  position: relative;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 22px;
  border-radius: 14px;
  border: 1px solid rgba(31, 112, 104, 0.22);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);

  flex-wrap: wrap;
}

.pulse-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.pulse-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
}

.pulse-dot.is-live {
  background: var(--color-accent);
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.pulse-dot.is-standby {
  background: var(--color-on-inverse-muted);
  outline: 2px solid var(--color-border);
  outline-offset: 2px;
}

.pulse-label {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.pulse-value {
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--color-brand-strong);
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  white-space: nowrap;
}

.pulse-divider {
  width: 1px;
  height: 22px;
  background: rgba(31, 112, 104, 0.18);
}

.section-wrap {
  max-width: 1200px;
  margin: 0 auto;
  padding: 46px 18px;
}

.section-title-wrap {
  margin-bottom: 20px;
}

.section-title {
  margin: 0;
  font-size: var(--text-3xl);
  font-weight: 700;
  color: var(--color-text);
}

.section-subtitle {
  margin: 8px 0 0;
  color: var(--color-text-muted);
}

.feature-grid,
.roles-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 12px;
}

.feature-card,
.role-card {
  background: var(--surface-gradient);
  border: 1px solid var(--color-on-inverse-muted);
  border-radius: 14px;
  padding: 18px;
}

/* 功能卡整卡可点，箭头示能取代同构按钮行 */
.feature-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.22s ease, border-color 0.22s ease;
}

.feature-card:hover {
  transform: translateY(-3px);
  border-color: rgba(20, 167, 154, 0.45);
}

.feature-card:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.card-go {
  position: absolute;
  top: 16px;
  right: 16px;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--el-color-primary);
  background: rgba(20, 167, 154, 0.1);
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.feature-card:hover .card-go,
.feature-card:focus-visible .card-go {
  opacity: 1;
  transform: translateX(0);
}

.role-card {
  position: relative;
  overflow: hidden;
  border-top: 3px solid var(--role-accent, var(--color-accent));
  transition: transform 0.22s ease;
}

.role-card:hover {
  transform: translateY(-3px);
}

.role-detail-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: var(--role-accent, var(--color-accent));
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.role-detail-arrow {
  transition: transform 0.2s ease;
}

.role-detail-btn:hover .role-detail-arrow {
  transform: translateX(3px);
}

.feature-icon,
.role-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--color-accent);
  color: var(--text-on-dark);
  font-size: var(--text-lg);
}

.feature-card h3,
.role-card h3 {
  margin: 14px 0 8px;
  font-size: var(--text-lg);
  font-weight: 600;
}

.feature-card p,
.role-card p {
  margin: 0 0 14px;
  color: var(--color-text-muted);
  line-height: 1.7;
  font-size: var(--text-sm);
}

.intro-section {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}

.intro-left {
  background: var(--surface-gradient);
  border: 1px solid var(--color-on-inverse-muted);
  border-radius: 14px;
  padding: 18px;
}

.intro-text {
  color: var(--color-text-muted);
  line-height: 1.9;
  margin: 10px 0 18px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(90px, 1fr));
  gap: 10px;
  transition: opacity 0.2s ease;
}

.metrics-grid.is-loading {
  opacity: 0.82;
}

.metric-card {
  background: var(--color-brand-strong);
  color: var(--color-on-inverse-muted);
  border-radius: 10px;
  padding: 10px;
  text-align: center;
}

.metric-value {
  font-size: clamp(0.85rem, 1.1vw, var(--text-lg));
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-label {
  font-size: var(--text-xs);
  margin-top: 4px;
  opacity: 0.86;
}

.intro-image {
  border-radius: 14px;
  min-height: 320px;
  background-size: cover;
  background-position: center;
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 10px;
}

.gallery-grid img {
  width: 100%;
  height: 190px;
  object-fit: cover;
  border-radius: 10px;
}

.compliance-strip {
  max-width: 1200px;
  margin: 0 auto;
  padding: 6px 18px 30px;
}

/* 发丝线分隔行替代徽章墙 */
.compliance-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  row-gap: 10px;
}

.compliance-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 2px 22px;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.compliance-item + .compliance-item {
  border-left: 1px solid rgba(31, 112, 104, 0.22);
}

.compliance-icon {
  color: var(--color-accent);
}

/* ── 角色权责详述弹窗 ── */
.role-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(4, 24, 26, 0.55);
  backdrop-filter: blur(4px);
}

.role-dialog-panel {
  width: min(520px, 100%);
  max-height: 84vh;
  overflow-y: auto;
  border-radius: 18px;
  background: var(--color-on-inverse);
  padding: 24px;
}

.role-dialog-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-dialog-dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: var(--role-accent, var(--color-accent));
  outline: 2px solid color-mix(in srgb, var(--role-accent, var(--color-accent)) 45%, transparent);
  outline-offset: 2px;
}

.role-dialog-head h3 {
  margin: 0;
  font-size: var(--text-xl);
  color: var(--color-text);
}

.role-dialog-close {
  margin-left: auto;
  border: none;
  background: transparent;
  font-size: 26px;
  line-height: 1;
  color: var(--color-text-muted);
  cursor: pointer;
}

.role-dialog-close:hover {
  color: var(--color-text);
}

.role-dialog-summary {
  margin: 14px 0;
  color: var(--color-text-muted);
  line-height: 1.8;
  font-size: var(--text-base);
}

.role-dialog-duties {
  list-style: none;
  margin: 0 0 20px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-dialog-duties li {
  position: relative;
  padding: 10px 14px 10px 34px;
  border-radius: 10px;
  background: var(--color-surface-elevated);
  color: var(--color-brand-muted);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.role-dialog-duties li::before {
  content: '✓';
  position: absolute;
  left: 13px;
  top: 10px;
  color: var(--role-accent, var(--color-accent));
  font-weight: 700;
}

.role-dialog-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.role-dialog-login {
  flex: 1;
  border: none;
  background: var(--brand-gradient);
  color: var(--text-on-dark);
  font-size: var(--text-base);
  font-weight: 600;
  padding: 12px 18px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.role-dialog-login:hover {
  transform: translateY(-1px);
}

.role-dialog-enter {
  flex: 1;
  border: 1px solid rgba(20, 163, 143, 0.4);
  background: var(--color-on-inverse);
  color: var(--color-accent);
  font-size: var(--text-base);
  font-weight: 600;
  padding: 12px 18px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.role-dialog-enter:hover {
  background: var(--color-raised);
}

.role-dialog-enter,
.role-dialog-login {
  text-align: center;
}

.role-dialog-overlay {
  transition: opacity 0.22s ease;
}

.role-dialog-panel {
  transition: transform 0.22s ease, opacity 0.22s ease;
}

.role-dialog-enter-active,
.role-dialog-leave-active {
  transition: opacity 0.22s ease;
}

.role-dialog-enter-from,
.role-dialog-leave-to {
  opacity: 0;
}

.role-dialog-enter-active .role-dialog-panel {
  transition: transform 0.24s ease;
}

.role-dialog-enter-from .role-dialog-panel {
  transform: translateY(14px) scale(0.98);
}

.footer {
  background: var(--color-brand-strong);
  color: var(--color-on-inverse-muted);
  padding: 34px 20px 20px;
}

.footer-columns {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 12px;
}

.footer h4 {
  margin: 0 0 12px;
  font-size: var(--text-md);
  font-weight: 600;
}

.footer a {
  display: block;
  color: var(--mint-400);
  text-decoration: none;
  margin-bottom: 8px;
  font-size: var(--text-sm);
}

.footer a:hover {
  color: var(--mint-100);
}

.footer-link {
  display: block;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--mint-400);
  margin-bottom: 8px;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}

.footer-link:hover {
  color: var(--mint-100);
}

.copyright {
  max-width: 1200px;
  margin: 22px auto 0;
  border-top: 1px solid rgba(196, 239, 229, 0.15);
  padding-top: 12px;
  font-size: var(--text-xs);
  color: var(--color-on-inverse-muted);
}

@media (max-width: 1024px) {
  .nav-menu {
    display: none;
  }

  .hamburger-btn {
    display: flex;
  }

  .feature-grid,
  .roles-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }

  .intro-section {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: repeat(3, minmax(90px, 1fr));
  }

  .hero-inner {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .hero-visual {
    order: -1;
  }

  .twin-card {
    width: min(440px, 100%);
  }

  .pulse-bar {
    margin-top: 20px;
    justify-content: flex-start;
    gap: 12px;
  }

  .pulse-divider {
    display: none;
  }
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 30px;
  }

  .feature-grid,
  .roles-grid,
  .gallery-grid,
  .footer-columns {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(90px, 1fr));
  }

  .nav-cta-group {
    display: none;
  }

  .hero-cta-row {
    width: 100%;
  }

  .hero-cta-primary,
  .hero-cta-ghost {
    flex: 1;
    justify-content: center;
    padding: 12px 16px;
    font-size: var(--text-base);
  }

  .hero-badges {
    gap: 6px;
  }

  .pulse-bar {
    padding: 12px 16px;
  }

  .pulse-item {
    flex-wrap: wrap;
  }

  .compliance-row {
    justify-content: flex-start;
  }

  .compliance-item {
    padding: 2px 14px;
  }

  .role-dialog-panel {
    padding: 20px;
  }

  .role-dialog-actions {
    flex-direction: column;
  }
}
</style>
