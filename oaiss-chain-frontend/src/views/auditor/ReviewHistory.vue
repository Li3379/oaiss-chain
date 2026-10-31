<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：核查员工作台 - 历史审核报告归档追溯 (Review History)
 * 【路由地址】：/auditor/review/history
 * 【访问权限】：ROLE_REVIEWER (第三方核查员)
 * 【界面可见功能】：
 *   1. 历史核查归档列表：展示当前核查员已办结的初审历史案件（报告编号、企业、结论）
 *   2. 审核结论与意见追溯：穿透查阅历史通过或驳回的评审记录及审核人批注
 *   3. 申报排放量与凭据回溯：查阅历史审定报告的原始因子与附件文件
 * 【对应后端 Controller & API】：
 *   - ReviewerController:
 *     * GET /api/v1/reviewer/history -> getReviewHistory() 分页查询审核历史归档
 * ==============================================================================
 */
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose, DataAnalysis, List } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { getReviewHistory } from '../../api/reviewer'
import type { CarbonReportResponse } from '../../types'
import { formatDateTime } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'

const { t } = useI18n()

type ReviewHistoryRow = CarbonReportResponse & {
  enterpriseName: string
  reviewResult?: number
}

const tableData = ref<ReviewHistoryRow[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

function normalizeReviewResult(status?: number) {
  if (status === 3 || status === 5) return 3
  if (status === 4) return 4
  return status
}

const fetchData = async () => {
  loading.value = true
  try {
    const response = await getReviewHistory({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
    tableData.value = response.items.map((row) => ({
      ...row,
      enterpriseName: row.enterpriseName || '-',
      reviewResult: normalizeReviewResult(row.status),
    }))
    total.value = response.total
  } catch {
    ElMessage.error(t('reviewHistory.loadFailed'))
  } finally {
    loading.value = false
  }
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const onCurrentChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

/** 首屏统计条：全部由已加载的历史归档数据推导，不新增接口请求 */
const overviewStats = computed(() => {
  const rows = tableData.value
  const approvedCount = rows.filter((row) => row.reviewResult === 3).length
  const rejectedCount = rows.filter((row) => row.reviewResult === 4).length
  const decidedCount = approvedCount + rejectedCount
  const passRate = decidedCount > 0 ? `${((approvedCount / decidedCount) * 100).toFixed(1)}%` : '0%'

  return [
    {
      label: t('reviewHistory.statArchived'),
      value: total.value,
      unit: t('reviewHistory.statUnitReports'),
      delta: t('reviewHistory.statArchivedHint', { count: rows.length }),
      tone: 'neu' as const,
      icon: DataAnalysis,
    },
    {
      label: t('reviewHistory.statApproved'),
      value: approvedCount,
      unit: t('reviewHistory.statUnitReports'),
      delta: t('reviewHistory.statApprovedHint'),
      tone: approvedCount > 0 ? ('pos' as const) : ('neu' as const),
      icon: CircleCheck,
    },
    {
      label: t('reviewHistory.statRejected'),
      value: rejectedCount,
      unit: t('reviewHistory.statUnitReports'),
      delta: t('reviewHistory.statRejectedHint'),
      tone: rejectedCount > 0 ? ('neg' as const) : ('neu' as const),
      icon: CircleClose,
    },
    {
      label: t('reviewHistory.statPassRate'),
      value: passRate,
      delta: t('reviewHistory.statPassRateHint', { count: decidedCount }),
      tone: decidedCount > 0 ? ('pos' as const) : ('neu' as const),
      icon: List,
    },
  ]
})

onMounted(() => fetchData())
</script>

<template>
  <section class="history-page pen-enter">
    <PageHeader
      :title="t('menu.reviewHistory')"
      :description="t('reviewHistory.pageDescription')"
      :crumbs="[t('reviewHistory.breadcrumbAudit'), t('reviewHistory.breadcrumbHistory')]"
    />

    <PageHint :text="t('pageHints.reviewHistory')" />

    <StatStrip :items="overviewStats" :loading="loading" />

    <DataPanel
      :title="t('reviewHistory.panelTitle')"
      :meta="t('reviewHistory.panelMeta', { total })"
    >
      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="reportNo" :label="t('reviewHistory.colReportNo')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="enterpriseName" :label="t('reviewHistory.colEnterpriseName')" min-width="180" />
        <el-table-column prop="title" :label="t('reviewHistory.colReportTitle')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="reviewResult" :label="t('reviewHistory.colResult')" min-width="110">
          <template #default="{ row }">
            <StatusTag
              domain="report"
              :status="row.reviewResult"
              :text="row.reviewResult === 3 ? t('reviewHistory.approved') : row.reviewResult === 4 ? t('reviewHistory.rejected') : '-'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="reviewComment" :label="t('reviewHistory.colComment')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="reviewTime" :label="t('reviewHistory.colReviewTime')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.reviewedAt || row.reviewTime || row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="onSizeChange"
          @current-change="onCurrentChange"
        />
      </template>
    </DataPanel>
  </section>
</template>

<style scoped>
.history-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
</style>
