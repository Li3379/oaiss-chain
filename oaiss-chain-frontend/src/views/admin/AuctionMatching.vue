<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：管理员端 - 双向拍卖自动撮合控制台 (Auction Matching Console)
 * 【路由地址】：/admin/trading/matching
 * 【访问权限】：ROLE_ADMIN (系统管理员)
 * 【界面可见功能】：
 *   1. 撮合运行态：自动撮合开关、出清周期、买/卖盘待撮合挂单数、上次成交时间
 *   2. 立即撮合：手动触发一轮出清（无需等待下一个自动周期），结果即时回显
 *   3. 全站成交明细：全平台撮合成交流水表，分页浏览
 *   4. 撮合机制释义（PageHint 白话说明）
 * 【对应后端 Controller & API】：
 *   - DoubleAuctionController:
 *     * GET  /api/v1/auction/matching-status -> getMatchingStatus() 查询自动撮合运行态
 *     * POST /api/v1/auction/match           -> executeMatching()   手动立即撮合一
 *     * GET  /api/v1/auction/all-results     -> getAllMatchResults() 全平台成交明细
 * 【设计说明】：
 *   撮合引擎是「批量集合竞价」，必须有外部时钟驱动。本项目的主路径是后端
 *   AuctionMatchingScheduler 每 30 秒自动出清；本页的手动触发只是「不等周期」的补偿，
 *   不是唯一入口——因此即便管理员从不打开本页面，市场也在持续撮合。
 * ==============================================================================
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Money, Refresh, Timer, TrendCharts } from '@element-plus/icons-vue'
import { executeMatching, getAllMatchResults, getMatchingStatus, type AuctionMatchingStatus } from '../../api/auction'
import type { MatchingResultResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import PageHint from '../../components/PageHint.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'

const { t } = useI18n()

const status = ref<AuctionMatchingStatus | null>(null)
const statusLoading = ref(false)
const matching = ref(false)

const matchRows = ref<MatchingResultResponse[]>([])
const resultsLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

/** 出清周期自动刷新运行态：让「上次成交」随后台撮合跳动，证明系统真的在自动撮合 */
let refreshTimer: ReturnType<typeof setInterval> | null = null
const REFRESH_INTERVAL_MS = 15000

async function loadStatus(): Promise<void> {
  statusLoading.value = true
  try {
    status.value = await getMatchingStatus()
  } catch {
    status.value = null
  } finally {
    statusLoading.value = false
  }
}

async function loadResults(): Promise<void> {
  resultsLoading.value = true
  try {
    const page = await getAllMatchResults({ pageNum: currentPage.value, pageSize: pageSize.value })
    matchRows.value = page.items || []
    total.value = page.total || 0
  } catch {
    ElMessage.error(t('auctionMatching.resultsLoadFailed'))
    matchRows.value = []
    total.value = 0
  } finally {
    resultsLoading.value = false
  }
}

async function onMatchNow(): Promise<void> {
  // 同步置位防抖：连点会在 await 间隙穿透，导致并发触发锁冲突噪音
  if (matching.value) return
  matching.value = true
  try {
    const results = await executeMatching()
    if (results.length > 0) {
      ElMessage.success(t('auctionMatching.runNowSuccess', { count: results.length }))
    } else {
      ElMessage.info(t('auctionMatching.runNowEmpty'))
    }
    await Promise.all([loadStatus(), loadResults()])
  } catch {
    ElMessage.error(t('auctionMatching.runNowFailed'))
    // 失败也刷新一次：可能是锁竞争，状态仍需与实际对齐
    await loadStatus()
  } finally {
    matching.value = false
  }
}

function onSizeChange(size: number): void {
  pageSize.value = size
  currentPage.value = 1
  loadResults()
}

function onPageChange(page: number): void {
  currentPage.value = page
  loadResults()
}

const statItems = computed(() => {
  const s = status.value
  const seconds = s?.intervalSeconds ?? 30
  // 状态接口失败时 status 为 null：此时不能断言「已停用」，那会把网络/部署故障
  // 误报成业务结论。显示「状态未知」并提示重试。
  const autoValue = s === null
    ? t('auctionMatching.statAutoUnknown')
    : (s.autoMatchingEnabled
      ? t('auctionMatching.statAutoOn', { seconds })
      : t('auctionMatching.statAutoOff'))
  return [
    {
      label: t('auctionMatching.statAuto'),
      value: autoValue,
      delta: s === null ? t('auctionMatching.statAutoUnknownHint') : t('auctionMatching.statAutoHint'),
      tone: s === null ? ('neu' as const) : (s.autoMatchingEnabled ? ('pos' as const) : ('neg' as const)),
      icon: Timer,
    },
    {
      label: t('auctionMatching.statPendingBuy'),
      value: s?.pendingBuyOrders ?? 0,
      unit: t('common.unit_order'),
      delta: t('auctionMatching.statPendingHint'),
      tone: 'neu' as const,
      icon: TrendCharts,
    },
    {
      label: t('auctionMatching.statPendingSell'),
      value: s?.pendingSellOrders ?? 0,
      unit: t('common.unit_order'),
      delta: t('auctionMatching.statPendingHint'),
      tone: 'neu' as const,
      icon: TrendCharts,
    },
    {
      label: t('auctionMatching.statLastMatch'),
      value: s?.lastMatchAt ? formatDateTime(s.lastMatchAt) : t('auctionMatching.statNever'),
      delta: t('auctionMatching.statLastMatchHint'),
      tone: s?.lastMatchAt ? ('pos' as const) : ('neu' as const),
      icon: Money,
    },
  ]
})

onMounted(() => {
  loadStatus()
  loadResults()
  refreshTimer = setInterval(loadStatus, REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <section class="matching-page pen-enter">
    <PageHeader
      :title="t('menu.auctionMatching')"
      :description="t('auctionMatching.pageDescription')"
      :crumbs="[t('auctionMatching.breadcrumbTrading'), t('auctionMatching.breadcrumbMatching')]"
      :actions="[{ label: t('auctionMatching.runNow'), variant: 'primary', loading: matching }]"
      @action="onMatchNow"
    />

    <PageHint :text="t('pageHints.auctionMatching')" />

    <StatStrip :items="statItems" :loading="statusLoading" />

    <DataPanel
      :title="t('auctionMatching.panelTitle')"
      :meta="t('auctionMatching.panelMeta', { total })"
    >
      <template #actions>
        <el-button :icon="Refresh" size="small" :loading="resultsLoading" @click="loadResults">
          {{ t('common.refresh') }}
        </el-button>
      </template>

      <el-table :data="matchRows" v-loading="resultsLoading" :empty-text="t('auctionMatching.emptyText')">
        <el-table-column prop="matchNo" :label="t('auctionMatching.colMatchNo')" min-width="170" />
        <el-table-column prop="buyOrderId" :label="t('auctionMatching.colBuyOrder')" min-width="110" />
        <el-table-column prop="sellOrderId" :label="t('auctionMatching.colSellOrder')" min-width="110" />
        <el-table-column prop="buyerName" :label="t('auctionMatching.colBuyer')" min-width="140" />
        <el-table-column prop="sellerName" :label="t('auctionMatching.colSeller')" min-width="140" />
        <el-table-column prop="matchedQuantity" :label="t('auctionMatching.colQuantity')" min-width="140">
          <template #default="{ row }">{{ formatNumber(row.matchedQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="settlementPrice" :label="t('auctionMatching.colPrice')" min-width="140">
          <template #default="{ row }">{{ formatNumber(row.settlementPrice) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="t('auctionMatching.colAmount')" min-width="140">
          <template #default="{ row }">{{ formatNumber(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="settledAt" :label="t('auctionMatching.colTime')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.settledAt || row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-pagination
          :current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </template>
    </DataPanel>
  </section>
</template>

<style scoped>
.matching-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.matching-page :deep(.pen-panel__foot) {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--pen-border);
}
</style>
