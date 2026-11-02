<script setup lang="ts">
/**
 * ==============================================================================
 * 【组件模块】：公共全局布局 - 系统骨架与导航中心 (Main Layout)
 * 【界面可见功能】：
 *   1. 响应式侧边栏菜单：按当前登录角色（企业/审核员/监管/管理员）动态渲染菜单项
 *   2. 顶部综合导航条：折叠侧边栏、当前路由面包屑、国际化语言切换、通知中心铃铛、用户头像下拉
 *   3. 个人中心快捷入口与安全退出登录
 *   4. 新手引导操作指南（Guided Tour）
 *   5. 用户会话在线停留活跃时长定时上报
 * 【对应后端 Controller & API】：
 *   - AuthController:
 *     * POST /api/v1/auth/logout     -> apiLogout() 安全登出
 *   - UserController:
 *     * POST /api/v1/user/stay-time  -> reportStayTime() 在线时长定时上报
 * ==============================================================================
 */
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Aim,
  Coin,
  Cpu,
  DataAnalysis,
  DataBoard,
  Document,
  Expand,
  Files,
  Fold,
  Histogram,
  Key,
  Link,
  Medal,
  Monitor,
  Search,
  Setting,
  Stamp,
  Switch,
  Tickets,
  TrendCharts,
  User,
  UserFilled,
  Wallet,
  Sunny,
  Moon,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { logout as apiLogout } from '../api/auth'
import { useAppStore } from '../store'
import AdmissionStatusBanner from '../components/AdmissionStatusBanner.vue'
import { MENU_BY_ROLE, RAIL_BY_ROLE, ROLE, type MenuItem, type RailItem } from '../config/menu'
import LanguageSwitcher from '../components/LanguageSwitcher.vue'
import NotificationBell from '../components/NotificationBell.vue'

/* 菜单模块图标映射（按业务模块 label key） */
const SECTION_ICONS: Record<string, Component> = {
  'menu.carbonAccounting': DataAnalysis,
  'menu.p2pOrderManage': Tickets,
  'menu.carbonTrading': Coin,
  'menu.companyInfo': TrendCharts,
  'menu.creditScore': Medal,
  'menu.carbonCoinAccount': Wallet,
  'menu.blockchain': Link,
  'menu.carbonNeutral': Aim,
  'menu.aiPrediction': Cpu,
  'menu.personalCenter': User,
  'menu.auditMaterial': Document,
  'menu.projectReview': Stamp,
  'menu.monitoringCenter': Monitor,
  'menu.systemManage': Setting,
  'menu.dataManage': DataBoard,
  'menu.certificationManage': Stamp,
  'menu.certificateManage': Key,
  'menu.searchCenter': Search,
}

/* 图标栏图标注册表：config/menu.ts 里的 RailItem.icon 是字符串名，这里解析为组件 */
const RAIL_ICONS: Record<string, Component> = {
  DataAnalysis,
  Tickets,
  Coin,
  Switch,
  TrendCharts,
  Medal,
  Wallet,
  Link,
  Aim,
  Cpu,
  User,
  Document,
  Stamp,
  Key,
  Monitor,
  Setting,
  DataBoard,
  Histogram,
  Files,
  Search,
}

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const { t } = useI18n()

const MOBILE_BREAKPOINT = 768
let mobileQuery: MediaQueryList | null = null

const onMobileChange = (e: MediaQueryListEvent): void => {
  if (e.matches) {
    appStore.sidebarCollapsed = true
  }
}

onMounted(() => {
  mobileQuery = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT}px)`)
  mobileQuery.addEventListener('change', onMobileChange)
  if (mobileQuery.matches) appStore.sidebarCollapsed = true
})


onUnmounted(() => {
  mobileQuery?.removeEventListener('change', onMobileChange)
})

const menuTree = computed<MenuItem[]>(() => MENU_BY_ROLE[appStore.role as keyof typeof MENU_BY_ROLE] || [])

const sectionIcon = (label: string): Component | undefined => SECTION_ICONS[label]

/* ---------- pen 壳层：顶栏一级页签 + 72px 图标栏 ---------- */
const TAB_LABEL: Record<string, string> = {
  carbon: 'layout.tabCarbon',
  trade: 'layout.tabTrade',
  asset: 'layout.tabAsset',
  intel: 'layout.tabIntel',
  audit: 'layout.tabAudit',
  project: 'layout.tabProject',
  verify: 'layout.tabVerify',
  monitor: 'layout.tabMonitor',
  system: 'layout.tabSystem',
  data: 'layout.tabData',
  cert: 'layout.tabCert',
  // 链上管理（管理员对账/死信）独立成一个顶栏页签。
  // 漏登记时界面会直接渲染字面量 "chain" 且不报错，只有截图才看得出。
  chain: 'layout.tabChain',
  search: 'layout.tabSearch',
}

/** 拍平所有叶子节点（保留父级 label 以便图标与提示） */
const flatLeaves = computed<MenuItem[]>(() => {
  const out: MenuItem[] = []
  for (const item of menuTree.value) {
    if (item.children?.length) out.push(...item.children)
    else if (item.path) out.push(item)
  }
  return out
})

/** 顶栏页签：按出现顺序去重 */
const tabs = computed<{ key: string; label: string }[]>(() => {
  const seen: string[] = []
  for (const leaf of flatLeaves.value) {
    if (leaf.tab && !seen.includes(leaf.tab)) seen.push(leaf.tab)
  }
  return seen.map((key) => ({ key, label: TAB_LABEL[key] || key }))
})

/** 当前路由所属页签：先精确匹配叶子，再按路径前缀兜底（覆盖 CarbonNeutralDetail 这类参数路由） */
const activeTab = computed<string>(() => {
  const exact = flatLeaves.value.find((l) => l.path === route.path)?.tab
  if (exact) return exact
  const prefix = flatLeaves.value.find((l) => l.path && route.path.startsWith(l.path + '/'))?.tab
  return prefix || tabs.value[0]?.key || ''
})

/** 图标栏条目：pen 稿的 11 个图标入口（分组只落一个首叶子，其余叶子由页签二级进入） */
const railItems = computed<RailItem[]>(
  () => RAIL_BY_ROLE[appStore.role as keyof typeof RAIL_BY_ROLE] || [],
)

/** 页签内的二级条目（rail === false 的叶子，属于当前页签） */
const tabSubItems = computed<MenuItem[]>(() =>
  flatLeaves.value.filter((l) => l.rail === false && l.tab === activeTab.value),
)

/** 图标栏高亮：路由命中条目的任一 match 片段即视为当前项（分组入口覆盖全部子页） */
const activeRail = computed<RailItem | undefined>(() => {
  const items = railItems.value
  return (
    items.find((item) => item.path === route.path) ||
    items.find((item) => item.match.some((p) => route.path === p || route.path.startsWith(p + '/')))
  )
})

const railIcon = (item: RailItem): Component | undefined => RAIL_ICONS[item.icon]

/** 页签切换：跳到该页签下第一个叶子 */
const onSelectTab = (key: string) => {
  const first = flatLeaves.value.find((l) => l.tab === key && l.path)
  if (first?.path && first.path !== route.path) router.push(first.path)
}


/* ---------- 新用户 3 步上手引导（仅首次访问，完成/跳过后不再弹出） ---------- */
// 键按用户隔离：旧实现使用全局常量键，导致「A 用户看过 → B 用户也看不到」，
// 且仅写 localStorage —— 隐身模式/存储被清理后会反复弹出（27 张截图被遮挡的根因）。
const TOUR_STORAGE_PREFIX = 'oaiss_guided_tour_done'
const tourStorageKey = (): string => {
  const userId = appStore.userId
  return userId ? `${TOUR_STORAGE_PREFIX}:${userId}` : TOUR_STORAGE_PREFIX
}
const tourVisible = ref(false)
const tourStep = ref(0)

function isTourDone(): boolean {
  const key = tourStorageKey()
  try {
    return window.localStorage.getItem(key) === '1' || window.sessionStorage.getItem(key) === '1'
  } catch {
    return false
  }
}

function persistTourDone(key: string): void {
  try {
    window.localStorage.setItem(key, '1')
  } catch {
    /* localStorage 不可用（隐私模式）时降级到 sessionStorage */
    try {
      window.sessionStorage.setItem(key, '1')
    } catch {
      /* 两者都不可用：仅本次会话内存生效，不再重复弹窗 */
    }
  }
}

const tourSteps = computed(() => [
  { title: t('guidedTour.step1Title'), text: t('guidedTour.step1Text') },
  { title: t('guidedTour.step2Title'), text: t('guidedTour.step2Text') },
  { title: t('guidedTour.step3Title'), text: t('guidedTour.step3Text') },
])

function markTourDone(): void {
  tourVisible.value = false
  persistTourDone(tourStorageKey())
}

function onTourNext(): void {
  if (tourStep.value < tourSteps.value.length - 1) {
    tourStep.value += 1
    return
  }
  markTourDone()
}

function onTourStart(): void {
  markTourDone()
  void router.push('/enterprise/carbon/upload')
}

onMounted(() => {
  const isEnterprise = appStore.role === ROLE.ENTERPRISE
  if (isEnterprise && !isTourDone()) {
    // 仅在首次访问时出现一次；标记写入放在用户关闭/跳过/完成后
    tourVisible.value = true
  }
})

// 用户直接点右上角 × 或点遮罩关闭时必须同样落盘，否则下一路由会再次弹出
watch(tourVisible, (visible) => {
  if (!visible) {
    persistTourDone(tourStorageKey())
  }
})


const activeMenu = computed(() => route.path)

const onSelectMenu = (index: string) => {
  if (index && index.startsWith('/') && index !== route.path) {
    router.push(index)
  }
}

const onLogout = async () => {
  try {
    await apiLogout()
  } catch {
    // 退出登录接口失败不影响本地退出
  }
  appStore.logout()
  ElMessage.success(t('layout.loggedOut'))
  router.replace('/login')
}

const isDark = ref(document.documentElement.classList.contains('dark'))

const toggleDark = () => {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark', isDark.value)
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
}

onMounted(() => {
  const saved = localStorage.getItem('theme')
  if (saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.documentElement.classList.add('dark')
    isDark.value = true
  }
})
</script>

<template>
  <el-container class="app-shell">
    <el-aside class="side-panel" :width="appStore.sidebarCollapsed ? '72px' : '260px'">
      <div class="brand-area">
        <div class="brand-badge">
          <el-icon :size="16"><Link /></el-icon>
        </div>
        <span v-show="!appStore.sidebarCollapsed" class="brand-title">{{ t('layout.brand') }}</span>
      </div>

      <!-- 折叠态：pen 稿 Rail（64×56，图标 18px + 短标签 9.5px，当前项浅底 + 左侧 2px 蓝条） -->
      <nav v-if="appStore.sidebarCollapsed" class="pen-rail" :aria-label="t('layout.primaryNav')">
        <button
          v-for="item in railItems"
          :key="item.path"
          type="button"
          class="pen-rail__item"
          :class="{ 'is-active': activeRail?.path === item.path }"
          :aria-label="t(item.label)"
          :aria-current="activeRail?.path === item.path ? 'page' : undefined"
          role="menuitem"
          @click="onSelectMenu(item.path)"
        >
          <span class="pen-rail__marker" aria-hidden="true" />
          <el-icon v-if="railIcon(item)" :size="18" class="pen-rail__icon">
            <component :is="railIcon(item)" />
          </el-icon>
          <span class="pen-rail__label">{{ t(item.railLabel) }}</span>
        </button>
      </nav>

      <el-scrollbar v-else class="menu-scrollbar">
        <el-menu
          class="side-menu"
          :default-active="activeMenu"
          :collapse="false"
          :collapse-transition="false"
          @select="onSelectMenu"
        >
          <!-- 展开：按分组呈现，分组标题不可点击，叶子逐条保留 -->
          <template v-for="item in menuTree" :key="item.label">
            <el-sub-menu
              v-if="item.children && item.children.length > 0"
              :index="item.label"
            >
              <template #title>
                <el-icon v-if="sectionIcon(item.label)" class="menu-icon">
                  <component :is="sectionIcon(item.label)" />
                </el-icon>
                <span>{{ t(item.label) }}</span>
              </template>

              <el-menu-item
                v-for="sub in item.children"
                :key="sub.path"
                :index="sub.path as string"
              >
                <span>{{ t(sub.label) }}</span>
              </el-menu-item>
            </el-sub-menu>

            <el-menu-item
              v-else-if="item.path"
              :index="item.path"
            >
              <el-icon v-if="sectionIcon(item.label)" class="menu-icon">
                <component :is="sectionIcon(item.label)" />
              </el-icon>
              <template #title>
                <span>{{ t(item.label) }}</span>
              </template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="top-header">
        <div class="header-left">
          <el-button
            class="collapse-btn"
            circle
            plain
            :aria-label="appStore.sidebarCollapsed ? t('layout.expandSidebar') : t('layout.collapseSidebar')"
            @click="appStore.toggleSidebar"
          >
            <el-icon>
              <Fold v-if="!appStore.sidebarCollapsed" />
              <Expand v-else />
            </el-icon>
          </el-button>

          <div class="system-meta">
            <div class="system-title">{{ t(appStore.systemTitle) }}</div>
            <div class="system-subtitle">{{ t('layout.systemDesc') }}</div>
          </div>

          <!-- pen 壳层：顶栏一级页签 -->
          <nav v-if="tabs.length > 1" class="top-tabs" :aria-label="t('layout.primaryNav')">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              type="button"
              class="top-tab"
              :class="{ 'is-active': tab.key === activeTab }"
              :aria-current="tab.key === activeTab ? 'page' : undefined"
              @click="onSelectTab(tab.key)"
            >
              {{ t(tab.label) }}
            </button>
          </nav>
        </div>

        <div class="header-right">
          <el-tag class="status-tag" effect="dark">{{ t('layout.monitorMode') }}</el-tag>
          <NotificationBell />
          <el-avatar :size="36" class="avatar-icon">
            <el-icon><UserFilled /></el-icon>
          </el-avatar>
          <div class="user-block">
            <div class="user-name">{{ appStore.username || t('layout.systemUser') }}</div>
            <div class="user-role">{{ t(appStore.roleLabel) }}</div>
          </div>
          <el-button
            class="theme-toggle"
            text
            :aria-label="t('layout.toggleTheme')"
            @click="toggleDark"
          >
            <el-icon><Moon v-if="!isDark" /><Sunny v-else /></el-icon>
          </el-button>
          <LanguageSwitcher />
          <el-button class="logout-btn" text @click="onLogout">{{ t('layout.logout') }}</el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <!-- 准入证书状态横幅：仅企业角色、且当前无有效证书时显示。
             后端门禁（7001）已生效，这里把「事后被拒」变成「事先告知」。 -->
        <AdmissionStatusBanner />
        <router-view />
      </el-main>
    </el-container>

    <el-dialog
      v-model="tourVisible"
      :title="t('guidedTour.title')"
      width="520px"
      :close-on-click-modal="true"
      append-to-body
      class="guided-tour-dialog"
    >
      <div class="tour-body">
        <p class="tour-step-index">{{ tourStep + 1 }} / {{ tourSteps.length }}</p>
        <h3 class="tour-step-title">{{ tourSteps[tourStep].title }}</h3>
        <p class="tour-step-text">{{ tourSteps[tourStep].text }}</p>
      </div>
      <template #footer>
        <div class="tour-footer">
          <el-button text @click="markTourDone">{{ t('guidedTour.skip') }}</el-button>
          <div class="tour-footer-right">
            <el-button v-if="tourStep > 0" @click="tourStep -= 1">{{ t('guidedTour.prev') }}</el-button>
            <el-button type="primary" @click="onTourNext">
              {{ tourStep < tourSteps.length - 1 ? t('guidedTour.next') : t('guidedTour.done') }}
            </el-button>
            <el-button type="success" plain @click="onTourStart">{{ t('guidedTour.start') }}</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.app-shell {
  height: 100%;
  width: 100%;
}

.tour-body {
  padding: 4px 2px 8px;
}

.tour-step-index {
  margin: 0 0 6px;
  color: var(--el-color-primary);
  font-size: var(--text-sm);
  font-weight: 600;
}

.tour-step-title {
  margin: 0 0 8px;
  color: var(--color-text);
  font-size: 17px;
}

.tour-step-text {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--text-base);
  line-height: 1.8;
}

.tour-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.tour-footer-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* Overflow chain fix: the inner flex child must be allowed to shrink below its
   content width, otherwise wide el-tables (~1340px of column min-widths) widen
   the whole document instead of scrolling inside the table body */
.app-shell > .el-container {
  min-width: 0;
  flex: 1 1 0%;
}

.app-shell > .el-container > .el-main {
  min-width: 0;
  width: 100%;
}

.side-panel {
  border-right: 1px solid var(--pen-border);
  background: var(--pen-shell);
  transition: width var(--motion-base) var(--motion-ease);
}

.brand-area {
  height: 56px;
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  padding: 0 var(--space-4);
  color: var(--pen-ink);
  border-bottom: 1px solid var(--pen-border);
}

/* 设计稿 Mark：26×26、2px 圆角、墨色底 */
.brand-badge {
  width: 26px;
  height: 26px;
  border-radius: var(--radius-control);
  display: grid;
  place-items: center;
  background: var(--pen-brand-mark);
  color: var(--color-on-inverse);
  box-shadow: none;
  flex-shrink: 0;
}

.brand-title {
  font-size: var(--fs-body);
  font-weight: 600;
  letter-spacing: 0.2px;
}

.menu-scrollbar {
  height: calc(100vh - 56px);
}

/* ---------- pen 图标栏 Rail：64×56、图标 18px + 短标签 9.5px ---------- */
.pen-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-0-5);
  padding: var(--space-3) 0;
  overflow-y: auto;
  height: calc(100vh - 56px);
  scrollbar-width: none;
}

.pen-rail::-webkit-scrollbar {
  display: none;
}

.pen-rail__item {
  position: relative;
  appearance: none;
  border: none;
  background: transparent;
  width: 64px;
  height: 56px;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border-radius: var(--radius-control);
  color: var(--pen-faint);
  font-family: inherit;
  font-size: 9.5px;
  font-weight: 400;
  line-height: 1;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--motion-ease),
    color var(--motion-fast) var(--motion-ease);
}

.pen-rail__item:hover {
  background: var(--pen-nav-active);
  color: var(--pen-ink);
}

.pen-rail__item:focus-visible {
  outline: 2px solid var(--pen-ink);
  outline-offset: -2px;
}

/* 当前项：浅底 + 左侧 2px 蓝色 Marker（设计稿 Marker 20px 高、圆角 1px） */
.pen-rail__item.is-active {
  background: var(--pen-nav-active);
  color: var(--pen-ink);
  font-weight: 500;
}

.pen-rail__marker {
  position: absolute;
  left: 0;
  top: 18px;
  width: 2px;
  height: 20px;
  border-radius: 1px;
  background: transparent;
}

.pen-rail__item.is-active .pen-rail__marker {
  background: var(--pen-chart);
}

.pen-rail__icon {
  flex-shrink: 0;
}

.pen-rail__label {
  font-size: 9.5px;
  line-height: 1;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.side-menu {
  border-right: none;
  background: transparent;
  --el-menu-base-level-padding: var(--space-2);
  --el-menu-level-padding: var(--space-2);
}

.side-menu :deep(.el-menu) {
  background: transparent;
  border-right: none;
}

/* 设计稿 Nav 项：64×56、2px 圆角、图标 18px + 文字 10.5px、当前项浅底 + 左侧蓝条 */
.side-menu :deep(.el-sub-menu__title),
.side-menu :deep(.el-menu-item) {
  color: var(--pen-muted);
  border-radius: var(--radius-control);
  margin: 2px var(--space-1);
  height: 44px;
  line-height: 44px;
  font-size: var(--fs-body);
  transition: background-color var(--motion-fast) var(--motion-ease),
    color var(--motion-fast) var(--motion-ease);
}

.side-menu :deep(.el-sub-menu__title .menu-icon),
.side-menu :deep(.el-menu-item .menu-icon) {
  color: var(--pen-faint);
  margin-right: var(--space-2);
  width: 18px;
  font-size: 18px;
}

.side-menu :deep(.el-sub-menu__title:hover),
.side-menu :deep(.el-menu-item:hover) {
  background: var(--pen-nav-active);
  color: var(--pen-ink);
}

/* 当前项：浅底 + 左侧 2px 蓝色标记（设计稿 Marker） */
.side-menu :deep(.el-menu-item.is-active) {
  color: var(--pen-ink);
  background: var(--pen-nav-active);
  font-weight: 500;
  box-shadow: inset 2px 0 0 0 var(--pen-chart);
}

.side-menu :deep(.el-menu-item.is-active .menu-icon) {
  color: var(--pen-ink);
}

/* 侧栏滚动条与文本选区（浏览器表面主题化） */
.menu-scrollbar :deep(.el-scrollbar__bar.is-vertical) {
  width: 4px;
  right: 2px;
}

.menu-scrollbar :deep(.el-scrollbar__thumb) {
  background: var(--color-border-soft);
}

.app-shell ::selection {
  background: var(--color-accent);
  color: var(--color-on-inverse);
}

/* 设计稿 TopBar：56px 高、左右 18px 内边距、白底 + 发丝线 */
.top-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-5);
  background: var(--pen-shell-top);
  border-bottom: 1px solid var(--pen-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.collapse-btn {
  border-color: var(--pen-border);
  background: var(--pen-surface);
  color: var(--pen-muted);
  flex-shrink: 0;
}

.collapse-btn:hover {
  border-color: var(--pen-border);
  background: var(--pen-raised);
  color: var(--pen-ink);
}

.system-meta {
  color: var(--pen-ink);
}

.system-title {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.system-subtitle {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
  margin-top: 2px;
}

/* ---------- pen 壳层：顶栏一级页签 ---------- */
/* 设计稿 Tabs：文字 13px，当前项墨色 + 2px 蓝色下划线 */
.top-tabs {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  margin-left: var(--space-4);
  flex-shrink: 0;
}

.top-tab {
  appearance: none;
  border: none;
  background: transparent;
  padding: 0;
  height: 54px;
  border-radius: 0;
  font-family: inherit;
  font-size: var(--fs-body);
  color: var(--pen-faint);
  cursor: pointer;
  position: relative;
  transition: color var(--motion-fast) var(--motion-ease);
}

.top-tab:hover {
  color: var(--pen-ink);
}

.top-tab:focus-visible {
  outline: 2px solid var(--pen-ink);
  outline-offset: -2px;
}

.top-tab.is-active {
  color: var(--pen-ink);
  font-weight: 500;
}

.top-tab.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  border-radius: 1px;
  background: var(--pen-chart);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
}

.logout-btn {
  color: var(--pen-muted);
  padding: 0 4px;
  font-size: var(--fs-sub);
}

.logout-btn:hover {
  color: var(--pen-ink);
}

.theme-toggle {
  color: var(--pen-muted);
  padding: 0 4px;
  font-size: 16px;
}

.theme-toggle:hover {
  color: var(--pen-ink);
}

.status-tag {
  background: transparent;
  border: 1px solid var(--pen-border);
  color: var(--pen-muted);
  height: 23px;
  font-size: var(--fs-pill);
}

.avatar-icon {
  color: var(--pen-muted);
  background: var(--pen-raised);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-control);
}

.user-name {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--pen-ink);
}

.user-role {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
  margin-top: 1px;
}

.main-content {
  min-height: calc(100vh - 56px);
  max-width: 1600px;
  margin: 0 auto;
  background: var(--pen-ground);
  padding: var(--space-6) var(--space-7);
}

@media (max-width: 768px) {
  .side-panel {
    position: fixed;
    z-index: 100;
    height: 100vh;
  }

  /* 侧栏固定悬浮后脱离文档流：主容器补偿折叠宽度，避免内容被遮挡 */
  .app-shell > .el-container {
    padding-left: 72px;
  }

  .top-header {
    height: auto;
    padding: 8px 12px;
    flex-wrap: wrap;
    gap: 8px;
  }

  .system-subtitle,
  .status-tag {
    display: none;
  }

  /* 窄屏标题保持单行，避免逐字竖排溢出 */
  .system-title {
    font-size: var(--text-base);
    white-space: nowrap;
  }

  .header-left {
    min-width: 0;
    flex: 1 1 auto;
  }

  .user-block {
    display: none;
  }

  .header-right {
    gap: 6px;
  }

  .main-content {
    padding: var(--spacing-mobile) !important;
  }
}
</style>
