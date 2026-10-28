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
  Fold,
  Key,
  Link,
  Medal,
  Monitor,
  Setting,
  Stamp,
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
import { MENU_BY_ROLE, ROLE, type MenuItem } from '../config/menu'
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

/** 图标栏条目：rail === true 的叶子 */
const railItems = computed<MenuItem[]>(() => flatLeaves.value.filter((l) => l.rail === true))

/** 页签内的二级条目（rail === false 的叶子，属于当前页签） */
const tabSubItems = computed<MenuItem[]>(() =>
  flatLeaves.value.filter((l) => l.rail === false && l.tab === activeTab.value),
)

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
    <el-aside class="side-panel" :width="appStore.sidebarCollapsed ? '70px' : '260px'">
      <div class="brand-area">
        <div class="brand-badge">
          <el-icon :size="16"><Link /></el-icon>
        </div>
        <span v-show="!appStore.sidebarCollapsed" class="brand-title">{{ t('layout.brand') }}</span>
      </div>

      <el-scrollbar class="menu-scrollbar">
        <el-menu
          class="side-menu"
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          @select="onSelectMenu"
        >
          <!-- 折叠：72px 图标栏，悬停显示名称（aria-label 保住 e2e 的 menuitem 可访问名） -->
          <template v-if="appStore.sidebarCollapsed">
            <el-menu-item
              v-for="item in railItems"
              :key="item.path"
              :index="item.path as string"
              :aria-label="t(item.label)"
            >
              <el-icon v-if="sectionIcon(item.label)" class="menu-icon">
                <component :is="sectionIcon(item.label)" />
              </el-icon>
              <template #title>
                <span>{{ t(item.label) }}</span>
              </template>
            </el-menu-item>
          </template>

          <!-- 展开：按 pen 稿分组呈现，分组标题不可点击，叶子逐条保留 -->
          <template v-else>
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
  color: var(--text-primary);
  font-size: 17px;
}

.tour-step-text {
  margin: 0;
  color: var(--text-secondary);
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
  border-right: 1px solid var(--color-border);
  background: var(--color-ground);
}

.brand-area {
  height: 66px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
}

.brand-badge {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--color-accent);
  color: var(--color-on-inverse);
  box-shadow: none;
  flex-shrink: 0;
}

.brand-title {
  font-size: var(--text-md);
  font-weight: 700;
  letter-spacing: 0.6px;
}

.menu-scrollbar {
  height: calc(100vh - 66px);
}

.side-menu {
  border-right: none;
  background: transparent;
  --el-menu-base-level-padding: 18px;
  --el-menu-level-padding: 18px;
}

.side-menu :deep(.el-menu) {
  background: transparent;
  border-right: none;
}

.side-menu :deep(.el-sub-menu__title),
.side-menu :deep(.el-menu-item) {
  color: var(--color-text);
  border-radius: var(--radius-card);
  margin: 2px 10px;
  height: 44px;
  line-height: 44px;
  transition: background 0.18s ease, color 0.18s ease;
}

.side-menu :deep(.el-sub-menu__title .menu-icon),
.side-menu :deep(.el-menu-item .menu-icon) {
  color: var(--color-text-muted);
  margin-right: 8px;
  width: 18px;
}

.side-menu :deep(.el-sub-menu__title:hover),
.side-menu :deep(.el-menu-item:hover) {
  background: var(--color-raised);
  color: var(--color-text);
}

.side-menu :deep(.el-menu-item.is-active) {
  color: var(--color-text);
  background: var(--color-raised);
  font-weight: 600;
  box-shadow: inset 2px 0 0 0 var(--color-accent);
}

.side-menu :deep(.el-menu-item.is-active .menu-icon) {
  color: var(--color-accent);
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

.top-header {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.collapse-btn {
  border-color: var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.collapse-btn:hover {
  border-color: var(--color-border);
  background: var(--color-raised);
  color: var(--color-text);
}

.system-meta {
  color: var(--color-text);
}

.system-title {
  font-size: var(--text-lg);
  font-weight: 700;
  letter-spacing: 0.3px;
}

.system-subtitle {
  font-size: var(--text-xs);
  opacity: 0.78;
  margin-top: 3px;
}

/* ---------- pen 壳层：顶栏一级页签 ---------- */
.top-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 18px;
  flex-shrink: 0;
}

.top-tab {
  appearance: none;
  border: none;
  background: transparent;
  padding: 0 12px;
  height: 32px;
  border-radius: var(--radius-control);
  font-family: inherit;
  font-size: var(--text-base);
  color: var(--color-text-muted);
  cursor: pointer;
  position: relative;
  transition: background 0.15s ease, color 0.15s ease;
}

.top-tab:hover {
  background: var(--color-raised);
  color: var(--color-text);
}

.top-tab:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.top-tab.is-active {
  color: var(--color-text);
  font-weight: 600;
}

.top-tab.is-active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: -1px;
  height: 2px;
  border-radius: 1px;
  background: var(--color-accent);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logout-btn {
  color: var(--color-text-muted);
  padding: 0 6px;
}

.logout-btn:hover {
  color: var(--color-text);
}

.theme-toggle {
  color: var(--color-text-muted);
  padding: 0 6px;
  font-size: var(--text-lg);
}

.theme-toggle:hover {
  color: var(--color-text);
}

.status-tag {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-muted);
}

.avatar-icon {
  color: var(--color-text);
  background: var(--color-raised);
}

.user-name {
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--color-text);
}

.user-role {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  margin-top: 2px;
}

.main-content {
  min-height: calc(100vh - 70px);
  max-width: 1600px;
  margin: 0 auto;
  background: var(--color-ground);
}

@media (max-width: 768px) {
  .side-panel {
    position: fixed;
    z-index: 100;
    height: 100vh;
  }

  /* 侧栏固定悬浮后脱离文档流：主容器补偿折叠宽度，避免内容被遮挡 */
  .app-shell > .el-container {
    padding-left: 70px;
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
