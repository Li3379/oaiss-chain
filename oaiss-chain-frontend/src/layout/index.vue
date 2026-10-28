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

const defaultOpeneds = computed(() => {
  const openList: string[] = []

  menuTree.value.forEach((item) => {
    if (item.children?.some((child) => child.path === route.path)) {
      openList.push(item.label)
    }
  })

  return openList
})

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
          :default-openeds="defaultOpeneds"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          @select="onSelectMenu"
        >
          <template v-for="item in menuTree" :key="item.label">
            <!-- 1. 包含多个子页面的可展开分组菜单 -->
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
                :index="sub.path"
              >
                <span>{{ t(sub.label) }}</span>
              </el-menu-item>
            </el-sub-menu>

            <!-- 2. 单页面直接跳转菜单项 -->
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
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  background: var(--chrome-gradient-side);
}

.brand-area {
  height: 66px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  color: var(--text-on-dark-muted);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-badge {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--chrome-gradient-accent);
  color: #0d3236;
  box-shadow: 0 6px 14px rgba(24, 152, 138, 0.35);
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
  color: rgba(235, 248, 247, 0.87);
  border-radius: 8px;
  margin: 2px 10px;
  height: 44px;
  line-height: 44px;
  transition: background 0.18s ease, color 0.18s ease;
}

.side-menu :deep(.el-sub-menu__title .menu-icon),
.side-menu :deep(.el-menu-item .menu-icon) {
  color: rgba(122, 214, 194, 0.85);
  margin-right: 8px;
  width: 18px;
}

.side-menu :deep(.el-sub-menu__title:hover),
.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.side-menu :deep(.el-menu-item.is-active) {
  color: #ffffff;
  background: linear-gradient(90deg, rgba(38, 204, 162, 0.42), rgba(38, 204, 162, 0.16));
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(122, 240, 210, 0.22);
}

.side-menu :deep(.el-menu-item.is-active .menu-icon) {
  color: #9df0d6;
}

/* 侧栏滚动条与文本选区（浏览器表面主题化） */
.menu-scrollbar :deep(.el-scrollbar__bar.is-vertical) {
  width: 4px;
  right: 2px;
}

.menu-scrollbar :deep(.el-scrollbar__thumb) {
  background: rgba(189, 243, 230, 0.28);
}

.app-shell ::selection {
  background: rgba(38, 204, 162, 0.32);
}

.top-header {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--chrome-gradient-top);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
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
  border-color: rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-on-dark-muted);
  flex-shrink: 0;
}

.collapse-btn:hover {
  border-color: rgba(255, 255, 255, 0.26);
  background: rgba(255, 255, 255, 0.14);
  color: var(--text-on-dark-muted);
}

.system-meta {
  color: var(--text-on-dark-muted);
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

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logout-btn {
  color: var(--text-on-dark-muted);
  padding: 0 6px;
}

.logout-btn:hover {
  color: var(--text-on-dark);
}

.theme-toggle {
  color: var(--text-on-dark-muted);
  padding: 0 6px;
  font-size: var(--text-lg);
}

.theme-toggle:hover {
  color: var(--text-on-dark);
}

.status-tag {
  background: var(--brand-gradient);
  border: none;
  color: var(--text-on-dark);
}

.avatar-icon {
  color: var(--chrome-selected-text);
  background: var(--chrome-selected-gradient);
}

.user-name {
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--text-on-dark-muted);
}

.user-role {
  font-size: var(--text-xs);
  color: rgba(240, 251, 246, 0.75);
  margin-top: 2px;
}

.main-content {
  min-height: calc(100vh - 70px);
  max-width: 1600px;
  margin: 0 auto;
  background:
    linear-gradient(180deg, rgba(20, 167, 154, 0.045) 0%, transparent 320px),
    var(--bg-page);
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
