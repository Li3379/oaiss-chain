<template>
  <section class="verify-page pen-enter">
    <PageHeader
      :title="t('verifyList.title')"
      :description="t('verifyList.description')"
      :crumbs="[t('verifyList.breadcrumbAdmin'), t('verifyList.breadcrumbVerify')]"
    />

    <PageHint :text="t('pageHints.verifyList')" />

    <StatStrip :items="overviewStats" :loading="statsLoading || loading" />

    <FilterBar :aria-label="t('verifyList.filterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('verifyList.colStatus') }}</span>
        <el-select v-model="statusFilter" :placeholder="t('verifyList.colStatus')" clearable style="width: 180px">
          <el-option :label="t('verifyList.statusPendingCertification')" :value="3" />
          <el-option :label="t('verifyList.statusApproved')" :value="5" />
          <el-option :label="t('verifyList.statusRejected')" :value="4" />
          <el-option :label="t('verifyList.statusPending')" :value="1" />
        </el-select>
      </label>

      <label class="pen-field">
        <span class="pen-field__label">{{ t('common.keyword') }}</span>
        <el-input v-model="keyword" :placeholder="t('common.enterKeyword')" clearable style="width: 300px" />
      </label>

      <el-button type="primary" @click="loadReports">{{ t('common.search') }}</el-button>
      <el-button
        type="success"
        :disabled="!selectedReports.length"
        :loading="batchCertifying"
        @click="handleBatchCertify"
      >
        {{ t('verifyList.btnBatchApprove') }} {{ selectedReports.length ? `(${selectedReports.length})` : '' }}
      </el-button>
    </FilterBar>

    <DataPanel
      :title="t('verifyList.panelTitle')"
      :meta="t('verifyList.panelMeta', { total })"
    >
      <template #actions>
        <span class="stats-row">
          <span class="status-label">{{ t('verifyList.blockchainStatus') }}</span>
          <el-tag :type="blockchainHealthy ? 'success' : 'danger'">
            {{ blockchainStatus }}
          </el-tag>
        </span>
      </template>

      <el-table
        :data="reports"
        :loading="loading"
        :empty-text="t('verifyList.emptyText')"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" :selectable="canCertify" width="50" />
        <el-table-column :label="t('verifyList.colIndex')" width="80">
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="reportNo" :label="t('verifyList.colReportNo')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="enterpriseName" :label="t('verifyList.colEnterpriseName')" min-width="150" />
        <el-table-column prop="accountingPeriod" :label="t('verifyList.colAccountingPeriod')" min-width="120" />
        <el-table-column prop="totalEmission" :label="t('verifyList.colTotalEmission')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('verifyList.colStatus')" min-width="120">
          <template #default="{ row }">
            <StatusTag domain="report" :status="row.status" :text="getStatusLabel(row.status)" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('verifyList.colSubmitTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('verifyList.colOperation')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row)">{{ t('verifyList.btnView') }}</el-button>
            <el-button
              v-if="canCertify(row)"
              type="success"
              link
              @click="onVerify(row, true)"
            >
              {{ t('verifyList.btnApprove') }}
            </el-button>
            <el-button
              v-if="canCertify(row)"
              type="danger"
              link
              @click="onVerify(row, false)"
            >
              {{ t('verifyList.btnReject') }}
            </el-button>
          </template>
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

    <el-dialog v-model="detailVisible" :title="t('verifyList.title')" width="700px">
      <el-descriptions v-if="currentReport" :column="2" border>
        <el-descriptions-item :label="t('verifyList.colReportNo')">{{ currentReport.reportNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('verifyList.colEnterpriseName')">{{ currentReport.enterpriseName }}</el-descriptions-item>
        <el-descriptions-item :label="t('verifyList.colAccountingPeriod')">{{ currentReport.accountingPeriod }}</el-descriptions-item>
        <el-descriptions-item :label="t('verifyList.colStatus')">
          <StatusTag domain="report" :status="currentReport.status" :text="getStatusLabel(currentReport.status)" />
        </el-descriptions-item>
        <el-descriptions-item :label="t('verifyList.colTotalEmission')">
          {{ currentReport.totalEmission }} {{ t('common.unit_ton') }}
        </el-descriptions-item>
        <el-descriptions-item>
          <template #label><span>{{ t('emissionPanel.scope1') }}<TermTip term="scope1" /></span></template>{{ currentReport.scope1Emission ?? '-' }} {{ t('common.unit_ton') }}</el-descriptions-item>
        <el-descriptions-item>
          <template #label><span>{{ t('emissionPanel.scope2') }}<TermTip term="scope2" /></span></template>{{ currentReport.scope2Emission ?? '-' }} {{ t('common.unit_ton') }}</el-descriptions-item>
        <el-descriptions-item>
          <template #label><span>{{ t('emissionPanel.scope3') }}<TermTip term="scope3" /></span></template>{{ currentReport.scope3Emission ?? '-' }} {{ t('common.unit_ton') }}</el-descriptions-item>
        <el-descriptions-item :label="t('verifyList.colSubmitTime')">{{ formatDateTime(currentReport.createdAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('verifyList.labelReviewComment')" :span="2">
          {{ currentReport.reviewComment || t('verifyList.noComment') }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="currentReport" class="cert-detail-block">
        <div class="cert-detail-title">{{ t('verifyList.emissionDetail') }}</div>
        <EmissionDataPanel :data="currentReport.emissionData" />
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：审核员端 - 碳报告上链补救与批量存证 (Verify List)
 * 【路由地址】：/auditor/verify/list（唯一入口）
 * 【访问权限】：ROLE_REVIEWER（主体）/ ROLE_ADMIN（仅直接 URL 兜底，无独立菜单）
 * 【界面可见功能】：
 *   1. 待审/已认证/已驳回状态指标卡片与底层区块链运行健康度指示灯
 *   2. 报告筛选栏：按状态（待认证/已上链/已驳回/待初审）与关键字检索
 *   3. 单条上链补救：点击【上链补救】或【驳回】，处理自动上链失败或审核后驳回
 *   4. 批量上链补救：勾选多条记录后点击【批量上链补救】，批量调用 Fabric 存证
 *
 * 【方案 A 说明（2026-10-21）】：上链已并入审核通过流程（POST /carbon/review 自动上链），
 * 本页面承担「自动上链失败的幂等补救」职责，由审核员使用。
 *   5. 报告审查详情对话框：查看范围一/二/三排放、审查意见与结构化排放面板
 *
 * 【单入口说明（2026-10-28）】：管理员端原有「认证列表」(/admin/verify/list) 与本页
 * 渲染同一组件、同一动作，属功能重复，已随菜单与路由一并取消；管理员仍可通过
 * 直接 URL 访问本页作为运维兜底（无需第二条路由）。
 * 【对应后端 Controller & API】：
 *   - CarbonController:
 *     * GET  /api/v1/carbon/reports       -> getReportList() 分页查询全平台报告
 *     * POST /api/v1/carbon/certify       -> certifyReport() 单条上链补救
 *     * POST /api/v1/carbon/certify/batch -> batchCertifyReports() 批量上链补救
 *   - BlockchainController:
 *     * GET  /api/v1/blockchain/status    -> getStatus() 获取底层区块链运行状态
 * ==============================================================================
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleClose, Link, Refresh, TrendCharts } from '@element-plus/icons-vue'
import { batchCertifyReports, certifyReport, getReportList } from '../../api/carbon'
import { getStatus } from '../../api/blockchain'
import type { CarbonReportResponse } from '../../types'
import { formatDateTime } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import TermTip from '../../components/TermTip.vue'
import EmissionDataPanel from '../../components/EmissionDataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'

const { t } = useI18n()

type ReportRow = CarbonReportResponse

type VerifyActionError = Error & {
  response?: {
    data?: {
      message?: string
    }
  }
}

const reports = ref<ReportRow[]>([])
const loading = ref(false)
const selectedReports = ref<ReportRow[]>([])
const batchCertifying = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const statusFilter = ref<number | ''>('')

const stats = ref({ pending: 0, approved: 0, rejected: 0 })
const blockchainStatus = ref('')
const statsLoading = ref(false)

const detailVisible = ref(false)
const currentReport = ref<ReportRow | null>(null)

const STATUS_LABEL_MAP: Record<number, string> = {
  0: 'statusDraft',
  1: 'statusPending',
  2: 'statusInReview',
  3: 'statusPendingCertification',
  4: 'statusRejected',
  5: 'statusOnChain',
}

const STATUS_FALLBACK_LABEL_MAP: Record<number, string> = {
  0: 'Draft',
  1: 'Pending',
  2: 'In Review',
  3: 'Pending Certification',
  4: 'Rejected',
  5: 'On Chain',
}

const STATUS_TYPE_MAP: Record<number, string> = {
  0: 'info',
  1: 'warning',
  2: 'primary',
  3: 'success',
  4: 'danger',
  5: 'success',
}

const HEALTHY_BLOCKCHAIN_STATUSES = new Set(['', 'normal', 'online', 'healthy'])

const blockchainHealthy = computed(() => HEALTHY_BLOCKCHAIN_STATUSES.has(blockchainStatus.value.trim().toLowerCase()))

function getStatusLabel(status?: number): string {
  const key = typeof status === 'number' ? STATUS_LABEL_MAP[status] : ''
  if (!key) return String(status ?? '-')

  const translated = t(`verifyList.${key}`)
  return translated === `verifyList.${key}`
    ? (typeof status === 'number' ? STATUS_FALLBACK_LABEL_MAP[status] : translated)
    : translated
}

function canCertify(row: ReportRow): boolean {
  return Number(row.status) === 3
}

function recomputeStats(rows: ReportRow[]): void {
  stats.value = {
    pending: rows.filter((row) => Number(row.status) === 3).length,
    approved: rows.filter((row) => Number(row.status) === 5).length,
    rejected: rows.filter((row) => Number(row.status) === 4).length,
  }
}

/** 首屏统计条：全部由已加载的本页数据与区块链状态推导，不新增接口请求 */
const overviewStats = computed(() => {
  const { pending, approved, rejected } = stats.value
  const reviewed = approved + rejected
  const passRate = reviewed > 0 ? Number(((approved / reviewed) * 100).toFixed(1)) : 0

  return [
    {
      label: t('verifyList.statPending'),
      value: pending,
      unit: t('verifyList.statUnitReports'),
      delta: t('verifyList.statPendingHint'),
      tone: pending > 0 ? ('neu' as const) : ('pos' as const),
      icon: Refresh,
    },
    {
      label: t('verifyList.statApproved'),
      value: approved,
      unit: t('verifyList.statUnitReports'),
      delta: `${t('verifyList.statApprovedHint')} · ${passRate}%`,
      tone: 'pos' as const,
      icon: Link,
    },
    {
      label: t('verifyList.statRejected'),
      value: rejected,
      unit: t('verifyList.statUnitReports'),
      delta: t('verifyList.statRejectedHint'),
      tone: rejected > 0 ? ('neg' as const) : ('neu' as const),
      icon: CircleClose,
    },
    {
      label: t('verifyList.statPassRate'),
      value: passRate,
      unit: '%',
      delta: t('verifyList.statPassRateHint', { reviewed }),
      tone: passRate >= 50 ? ('pos' as const) : ('neu' as const),
      icon: TrendCharts,
    },
  ]
})

async function loadReports(): Promise<void> {
  loading.value = true
  try {
    const result = await getReportList({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value),
    } as Parameters<typeof getReportList>[0] & { status?: number })

    reports.value = result.items
    total.value = result.total
    recomputeStats(reports.value)
  } catch {
    ElMessage.error(t('verifyList.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadBlockchainStatus(): Promise<void> {
  statsLoading.value = true
  try {
    const result = await getStatus()
    blockchainStatus.value = result == null || result.connected === false ? 'offline' : 'healthy'
  } catch {
    blockchainStatus.value = t('verifyList.blockchainNormal')
  } finally {
    statsLoading.value = false
  }
}

function viewDetail(row: ReportRow): void {
  currentReport.value = row
  detailVisible.value = true
}

function isCancelAction(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}

function getActionErrorMessage(error: unknown): string {
  const candidate = error as VerifyActionError | undefined
  return candidate?.response?.data?.message || candidate?.message || ''
}

async function onVerify(row: ReportRow, approved: boolean): Promise<void> {
  const actionText = approved ? t('verifyList.btnApprove') : t('verifyList.btnReject')

  try {
    if (approved) {
      const confirmHtml = `
        <div style="font-size:14px; line-height:1.6; color:var(--color-text);">
          <p style="margin:0 0 10px;">${t('verifyList.confirmApprove')}</p>
          <div style="padding:10px 12px; background:rgba(20, 167, 154, 0.08); border:1px solid rgba(20, 167, 154, 0.3); border-radius:8px; font-size:13px; color:var(--color-text-muted);">
            <div style="font-weight:600; color:var(--el-color-primary); margin-bottom:4px; display:flex; align-items:center; gap:6px;">
              <svg width="14" height="14" viewBox="0 0 1024 1024" fill="currentColor" style="flex-shrink:0;"><path d="M624 352h-96c-17.7 0-32 14.3-32 32s14.3 32 32 32h96c106 0 192 86 192 192s-86 192-192 192h-96c-106 0-192-86-192-192 0-17.7-14.3-32-32-32s-32 14.3-32 32c0 141.4 114.6 256 256 256h96c141.4 0 256-114.6 256-256s-114.6-256-256-256z"/><path d="M400 672h96c17.7 0 32-14.3 32-32s-14.3-32-32-32h-96c-106 0-192-86-192-192s86-192 192-192h96c106 0 192 86 192 192 0 17.7 14.3 32 32 32s32-14.3 32-32c0-141.4-114.6-256-256-256h-96C258.6 160 144 274.6 144 416s114.6 256 256 256z"/></svg>
              <span>${t('attestation.cardTitle')}</span>
            </div>
            <div>• ${t('attestation.targetReport')}: <code>${row.reportNo}</code></div>
            <div>• ${t('attestation.channel')}: <code>mychannel</code> | ${t('attestation.chaincode')}: <code>carbon-chaincode</code></div>
            <div style="margin-top:6px; font-size:12px; color:var(--el-color-warning);">${t('attestation.immutableWarning')}</div>
          </div>
        </div>
      `
      await ElMessageBox.confirm(confirmHtml, t('common.confirm'), {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        dangerouslyUseHTMLString: true,
        type: 'warning',
      })
    } else {
      await ElMessageBox.confirm(
        t('verifyList.confirmReject'),
        t('common.confirm'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'warning',
        },
      )
    }

    await certifyReport({
      reportId: Number(row.id),
      approved,
      comment: approved ? t('verifyList.approveSuccess') : t('verifyList.rejectSuccess'),
    })

    ElMessage.success(actionText)
    await loadReports()
  } catch (error: unknown) {
    if (!isCancelAction(error)) {
      const message = getActionErrorMessage(error)
      ElMessage.error(message ? `${t('verifyList.operationFailed')}: ${message}` : t('verifyList.operationFailed'))
    }
  }
}

function handleSelectionChange(rows: ReportRow[]): void {
  selectedReports.value = rows
}

async function handleBatchCertify(): Promise<void> {
  const ids = selectedReports.value.map(r => Number(r.id)).filter(id => Boolean(id))
  if (!ids.length) {
    ElMessage.warning(t('verifyList.noSelectedReports'))
    return
  }

  try {
    await ElMessageBox.confirm(
      t('verifyList.batchApproveConfirm', { count: ids.length }),
      t('common.confirm'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )

    batchCertifying.value = true
    const result = await batchCertifyReports({
      reportIds: ids,
      reviewResult: 5,
      reviewComment: t('verifyList.approveSuccess'),
    })

    ElMessage.success(
      `${t('verifyList.batchApproveSuccess', {
        success: result.successCount,
        failure: result.failureCount,
      })} ${t('nextSteps.afterBatchCertify')}`,
    )
    selectedReports.value = []
    await loadReports()
  } catch (error: unknown) {
    if (!isCancelAction(error)) {
      const message = getActionErrorMessage(error)
      ElMessage.error(message ? `${t('verifyList.batchApproveFailed')}: ${message}` : t('verifyList.batchApproveFailed'))
    }
  } finally {
    batchCertifying.value = false
  }
}

function onSizeChange(size: number): void {
  pageSize.value = size
  currentPage.value = 1
  void loadReports()
}

function onCurrentChange(page: number): void {
  currentPage.value = page
  void loadReports()
}

onMounted(() => {
  void loadReports()
  void loadBlockchainStatus()
})
</script>

<style scoped>
.verify-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.stats-row {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.status-label {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
}

.cert-detail-block {
  margin-top: var(--space-3);
}

.cert-detail-title {
  font-weight: 600;
  margin-bottom: var(--space-2);
  color: var(--pen-ink);
}
</style>
