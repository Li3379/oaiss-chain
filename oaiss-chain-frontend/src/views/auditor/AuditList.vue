<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：核查员工作台 - 碳排放申报数据初审 (Audit List)
 * 【路由地址】：/auditor/audit/list
 * 【访问权限】：ROLE_REVIEWER (第三方核查员)
 * 【界面可见功能】：
 *   1. 待审工单排队池：分页展示排碳企业提交的碳排放报告待审列表
 *   2. 报告审查详情抽屉：查阅范围一/二核算因子、能源消耗发票与检测佐证
 *   3. 数字签名在线验签：核验申报企业 RSA 私钥防伪签名的真实性与防篡改性
 *   4. 预置快捷驳回理由标签：一键填入“发票凭证缺失”、“核算参数自相矛盾”等标准理由
 *   5. 审核决议提交弹窗：录入专业核查建议，执行审核通过或驳回退改
 *   6. 个人核查绩效卡片：展示已审总数、初审通过率与平均审核时效
 * 【对应后端 Controller & API】：
 *   - CarbonController:
 *     * POST /api/v1/carbon/review             -> reviewReport() 提交初审结论（通过/驳回）
 *   - ReviewerController:
 *     * GET  /api/v1/reviewer/reports/pending  -> getPendingReports() 查询待审工单池
 *     * GET  /api/v1/reviewer/info             -> getReviewerInfo() 获取核查员档案
 *     * GET  /api/v1/reviewer/statistics       -> getStatistics() 获取核查绩效统计
 *     * GET  /api/v1/reviewer/qualification/my -> getMyReviewerQualification() 执业资质
 * ==============================================================================
 */
import { computed, onMounted, ref } from 'vue'
import { CircleCheck, CircleClose, Cpu, Key, Odometer, Warning } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reviewReport } from '../../api/carbon'
import { getPresignedUrl } from '../../api/file'
import {
  getMyReviewerQualification,
  getPendingReports,
  getReviewHistory,
  getReviewerInfo,
  getStatistics,
} from '../../api/reviewer'
import type {
  CarbonReportResponse,
  ReviewerInfoResponse,
  ReviewerQualificationResponse,
  ReviewerStatisticsResponse,
} from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'
import TermTip from '../../components/TermTip.vue'
import EmissionDataPanel from '../../components/EmissionDataPanel.vue'

const { t } = useI18n()

type ReviewerTableRow = CarbonReportResponse & {
  enterprise?: string
}

type ReviewFormState = {
  reportId: number | null
  approved: boolean
  comment: string
}

const EMPTY_REVIEW_FORM: ReviewFormState = {
  reportId: null,
  approved: true,
  comment: '',
}

const tableData = ref<ReviewerTableRow[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const activeTab = ref<'pending' | 'all'>('pending')
const statisticsData = ref<ReviewerStatisticsResponse | null>(null)
const reviewerInfoData = ref<ReviewerInfoResponse | null>(null)
const qualificationStatus = ref<ReviewerQualificationResponse | null>(null)

const reviewDialogVisible = ref(false)
const reviewForm = ref<ReviewFormState>({ ...EMPTY_REVIEW_FORM })
const reviewSubmitting = ref(false)

const quickRejectReasons = [
  'quickReasons.voucherMismatch',
  'quickReasons.factorTooLow',
  'quickReasons.missingInvoices',
  'quickReasons.periodConflict',
] as const

function applyQuickRejectReason(reasonKey: string) {
  const text = t(reasonKey)
  if (!reviewForm.value.comment) {
    reviewForm.value.comment = text
  } else if (!reviewForm.value.comment.includes(text)) {
    reviewForm.value.comment = `${reviewForm.value.comment}；${text}`
  }
}
const reviewingRow = ref<ReviewerTableRow | null>(null)

// 审核前验签状态（服务端在待审列表中已完成验签）：
// 无签名 null → 未签名；验签失败 → 红色警示，通过前必须显式确认
const rowSignatureValid = computed<boolean | null | undefined>(() => reviewingRow.value?.signatureValid)
const rowHasSignature = computed(() => Boolean(reviewingRow.value?.signatureData))

/* ---------- 佐证附件：解析 attachments 并换取预签名链接 ---------- */
// 新数据落库为 [{ name, objectName, url, size, hash }]；历史数据只有 url（未签名直连地址），
// 因此 objectName 缺失时从 URL 的 /{bucket}/ 之后反推，推不出来则禁用下载并提示。
type ReviewAttachment = {
  name: string
  objectName: string
  size: number
  hash?: string
  downloadable: boolean
}

/** 从裸直连 URL 反推 MinIO objectName：MinIO 为 path-style，首段是桶名，其后均为对象键 */
function inferObjectNameFromUrl(rawUrl: string): string {
  if (!rawUrl) return ''
  let pathname = ''
  try {
    pathname = new URL(rawUrl, window.location.origin).pathname
  } catch {
    return ''
  }
  const segments = pathname.split('/').filter(Boolean)
  if (segments.length < 2) return ''
  return segments
    .slice(1)
    .map((segment) => {
      try {
        return decodeURIComponent(segment)
      } catch {
        return segment
      }
    })
    .join('/')
}

const reviewAttachments = computed<ReviewAttachment[]>(() => {
  const raw = reviewingRow.value?.attachments
  if (!raw) return []

  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return []
  }
  if (!Array.isArray(parsed)) return []

  return parsed.flatMap((item, index): ReviewAttachment[] => {
    // 兼容最早期的「纯 URL 字符串数组」形态，与对象形态走同一套反推逻辑
    const record: Record<string, unknown> = typeof item === 'string'
      ? { url: item }
      : item && typeof item === 'object'
        ? (item as Record<string, unknown>)
        : {}
    if (Object.keys(record).length === 0) return []
    const url = typeof record.url === 'string' ? record.url : ''
    const explicitObjectName = typeof record.objectName === 'string' ? record.objectName.trim() : ''
    const objectName = explicitObjectName || inferObjectNameFromUrl(url)
    const size = Number(record.size)

    return [{
      name: typeof record.name === 'string' && record.name.trim()
        ? record.name
        : objectName.split('/').pop() || `attachment-${index + 1}`,
      objectName,
      size: Number.isFinite(size) ? size : 0,
      hash: typeof record.hash === 'string' ? record.hash : undefined,
      downloadable: Boolean(objectName),
    }]
  })
})

const attachmentDownloading = ref('')

function formatAttachmentSize(size: number): string {
  if (!size || size < 0) return '-'
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(0)} KB`
  return `${size} B`
}

/** 下载走预签名链接：私有桶裸 URL 无签名必 403，只有临时签名地址可读 */
async function onDownloadAttachment(attachment: ReviewAttachment): Promise<void> {
  if (!attachment.downloadable || attachmentDownloading.value) return
  attachmentDownloading.value = attachment.objectName
  try {
    const signedUrl = await getPresignedUrl(attachment.objectName)
    const link = document.createElement('a')
    link.href = signedUrl
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
    link.download = attachment.name
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  } catch {
    ElMessage.error(t('auditList.attachmentDownloadFailed'))
  } finally {
    attachmentDownloading.value = ''
  }
}

// 规则预检（非 AI 模型）：仅根据「验签结果 + 排放量 + 佐证附件」三类确定性事实生成提示，
// 不做推断、不调用任何模型端点。审核结论必须由审核员自行判断。
const rulePrecheck = computed(() => {
  if (!reviewingRow.value) return null
  const sigOk = reviewingRow.value.signatureValid !== false
  const total = Number(reviewingRow.value.totalEmission) || 0
  const hasAttachments = reviewAttachments.value.length > 0

  return {
    status: sigOk ? 'CHECK_PASS' : 'CHECK_ATTENTION',
    statusText: sigOk ? t('auditList.rulePrecheckPass') : t('auditList.rulePrecheckAttention'),
    statusType: (sigOk ? 'success' : 'warning') as 'success' | 'warning',
    findings: [
      sigOk
        ? t('auditList.ruleFindingSignatureOk')
        : t('auditList.ruleFindingSignatureInvalid'),
      total > 0
        ? t('auditList.ruleFindingTotalPresent', { total: total.toFixed(2) })
        : t('auditList.ruleFindingTotalMissing'),
      hasAttachments
        ? t('auditList.ruleFindingAttachmentsPresent', { count: reviewAttachments.value.length })
        : t('auditList.ruleFindingAttachmentsMissing'),
    ],
    recommendedComment: sigOk
      ? t('auditList.ruleCommentSignatureOk')
      : t('auditList.ruleCommentSignatureInvalid'),
  }
})

/** 仅把预检说明填入批注框，不代替审核员做通过/驳回的选择 */
const applyRulePrecheckComment = () => {
  if (!rulePrecheck.value) return
  reviewForm.value.comment = rulePrecheck.value.recommendedComment
  ElMessage.success(t('auditList.appliedRulePrecheckTip'))
}

const REVIEWABLE_STATUSES = new Set([1, 2])
const STATUS_TEXT_FALLBACK: Record<number, string> = {
  0: 'Draft',
  1: 'Submitted',
  2: 'Reviewing',
  3: 'Approved',
  4: 'Rejected',
  5: 'On Chain',
}

const normalizedStatistics = computed(() => {
  const stats = statisticsData.value ?? {}
  const totalReviews = Number(stats.completedReviews ?? stats.totalReviews ?? 0)
  const approvedCount = Number(stats.passedCount ?? stats.approvedCount ?? 0)
  const rejectedCount = Number(stats.rejectedCount ?? 0)
  const approvalRate = totalReviews > 0
    ? `${((approvedCount / totalReviews) * 100).toFixed(1)}%`
    : '0%'

  return {
    totalReviews,
    approvedCount,
    rejectedCount,
    approvalRate: String(stats.approvalRate ?? approvalRate),
  }
})

const qualificationStatusType = computed(() => {
  if (!qualificationStatus.value) return 'info'
  return qualificationStatus.value.status === 1 ? 'success' : 'danger'
})

const qualificationStatusText = computed(() => {
  if (!qualificationStatus.value) return t('certificateManage.notIssued')
  return qualificationStatus.value.status === 1 ? t('certificateManage.active') : t('certificateManage.revoked')
})

const reviewerIdentityLine = computed(() => {
  const info = reviewerInfoData.value
  if (!info) return ''

  const parts = [
    info.name ? `${t('auditList.reviewerName')}: ${info.name}` : '',
    info.organization ? `${t('auditList.organizationLabel')}: ${info.organization}` : '',
    info.qualificationNo ? `${t('certificateManage.colCertificateNo')}: ${info.qualificationNo}` : '',
  ].filter(Boolean)

  return parts.join(' | ')
})

function getStatusText(status?: number, statusText?: string): string {
  if (statusText) return statusText
  if (typeof status === 'number') return STATUS_TEXT_FALLBACK[status] || String(status)
  return '-'
}

function normalizeRow(row: CarbonReportResponse): ReviewerTableRow {
  const status = Number(row.status ?? 0)
  const enterpriseName = row.enterpriseName || (row.enterpriseId ? `Enterprise #${row.enterpriseId}` : '-')

  return {
    ...row,
    enterpriseName,
    status,
    statusText: getStatusText(status, row.statusText),
  }
}

async function fetchData(): Promise<void> {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    }
    const response = activeTab.value === 'pending'
      ? await getPendingReports(params)
      : await getReviewHistory(params)

    tableData.value = response.items.map(normalizeRow)
    total.value = response.total
  } catch {
    ElMessage.error(t('auditList.loadFailed'))
  } finally {
    loading.value = false
  }
}

function onSizeChange(size: number): void {
  pageSize.value = size
  currentPage.value = 1
  void fetchData()
}

function onCurrentChange(page: number): void {
  currentPage.value = page
  void fetchData()
}

function canReviewRow(row: ReviewerTableRow): boolean {
  return activeTab.value === 'pending' && REVIEWABLE_STATUSES.has(Number(row.status))
}

function openReviewDialog(row: ReviewerTableRow): void {
  if (!canReviewRow(row)) return

  reviewingRow.value = row
  reviewForm.value = {
    reportId: row.id,
    approved: true,
    comment: '',
  }
  reviewDialogVisible.value = true
}

function closeReviewDialog(): void {
  reviewDialogVisible.value = false
  reviewingRow.value = null
  reviewSubmitting.value = false
  reviewForm.value = { ...EMPTY_REVIEW_FORM }
}

async function submitReview(): Promise<void> {
  if (!reviewForm.value.reportId || reviewSubmitting.value) return

  // 审核意见是留档审计的依据：无论通过/驳回都必须留下一句结论
  if (!reviewForm.value.comment.trim()) {
    ElMessage.warning(t('auditList.commentRequired'))
    return
  }

  // 验签失败（数据可能被篡改）：通过前必须显式二次确认，杜绝无意识放行
  const approvingTampered = reviewForm.value.approved && rowSignatureValid.value === false
  if (approvingTampered) {
    try {
      await ElMessageBox.confirm(t('auditList.invalidApproveConfirm'), t('common.confirm'), {
        type: 'warning',
        confirmButtonText: t('auditList.approve'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return
    }
  }

  reviewSubmitting.value = true
  try {
    const resp = await reviewReport({
      reportId: reviewForm.value.reportId,
      approved: reviewForm.value.approved,
      comment: reviewForm.value.comment,
    })

    // 决策后仅展示一条结果消息（不再叠加签名提示与结论双 toast）
    if (reviewForm.value.approved) {
      if (resp?.signatureValid === false) {
        ElMessage.warning(t('auditList.invalidApproveWarn'))
      } else {
        ElMessage.success(`${t('auditList.approveSuccess')} ${t('nextSteps.afterReviewDone')}`)
      }
    } else {
      ElMessage.success(`${t('auditList.rejectSuccess')} ${t('nextSteps.afterReviewDone')}`)
    }
    closeReviewDialog()
    await Promise.all([fetchData(), fetchStatistics()])
  } catch {
    ElMessage.error(t('auditList.submitFailed'))
  } finally {
    reviewSubmitting.value = false
  }
}

async function fetchStatistics(): Promise<void> {
  try {
    statisticsData.value = await getStatistics()
  } catch {
    statisticsData.value = null
  }
}

async function fetchReviewerInfo(): Promise<void> {
  try {
    reviewerInfoData.value = await getReviewerInfo()
  } catch {
    reviewerInfoData.value = null
  }
}

function onTabChange(): void {
  currentPage.value = 1
  void fetchData()
}

async function fetchQualificationStatus(): Promise<void> {
  try {
    const list = await getMyReviewerQualification()
    qualificationStatus.value = list[0] ?? null
  } catch {
    qualificationStatus.value = null
  }
}

/** 首屏统计条：全部由已加载的核查绩效与本页工单推导，不新增接口请求 */
const overviewStats = computed(() => {
  const rows = tableData.value
  const pendingRows = rows.filter((row) => REVIEWABLE_STATUSES.has(Number(row.status))).length
  const stats = normalizedStatistics.value
  const decided = stats.approvedCount + stats.rejectedCount

  return [
    {
      label: t('auditList.statTotalReviews'),
      value: stats.totalReviews,
      unit: t('auditList.statUnitReports'),
      delta: t('auditList.statTotalReviewsHint', { count: pendingRows }),
      tone: 'neu' as const,
      icon: Odometer,
    },
    {
      label: t('auditList.statApproved'),
      value: stats.approvedCount,
      unit: t('auditList.statUnitReports'),
      delta: t('auditList.statApprovedHint'),
      tone: stats.approvedCount > 0 ? ('pos' as const) : ('neu' as const),
      icon: CircleCheck,
    },
    {
      label: t('auditList.statRejected'),
      value: stats.rejectedCount,
      unit: t('auditList.statUnitReports'),
      delta: t('auditList.statRejectedHint'),
      tone: stats.rejectedCount > 0 ? ('neg' as const) : ('neu' as const),
      icon: CircleClose,
    },
    {
      label: t('auditList.statPassRate'),
      value: stats.approvalRate,
      delta: t('auditList.statPassRateHint', { count: decided }),
      tone: decided > 0 ? ('pos' as const) : ('neu' as const),
      icon: Key,
    },
  ]
})

onMounted(() => {
  void fetchData()
  void fetchQualificationStatus()
  void fetchStatistics()
  void fetchReviewerInfo()
})
</script>

<template>
  <section class="audit-page pen-enter">
    <PageHeader
      :title="t('menu.carbonEmissionData')"
      :description="t('auditList.pageDescription')"
      :crumbs="[t('auditList.breadcrumbAudit'), t('auditList.breadcrumbData')]"
    />

    <PageHint :text="t('pageHints.auditList')" />

    <div class="reviewer-strip">
      <el-space>
        <span>{{ t('certificateManage.myQualification') }}:</span>
        <el-tag :type="qualificationStatusType">{{ qualificationStatusText }}</el-tag>
        <span v-if="qualificationStatus" class="identity-sub">
          {{ qualificationStatus.certificateNo }}
        </span>
      </el-space>
      <div v-if="reviewerIdentityLine" class="identity-line">
        {{ reviewerIdentityLine }}
      </div>
    </div>

    <StatStrip :items="overviewStats" :loading="loading" />

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane :label="t('auditList.tabPending')" name="pending" />
      <el-tab-pane :label="t('auditList.tabAllReports')" name="all" />
    </el-tabs>

    <DataPanel
      :title="t('auditList.panelTitle')"
      :meta="t('auditList.panelMeta', { total })"
    >
      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="reportNo" :label="t('auditList.colReportNo')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="enterpriseName" :label="t('auditList.colEnterpriseName')" min-width="180" />
        <el-table-column prop="title" :label="t('auditList.colReportTitle')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="totalEmission" :label="t('auditList.colTotalEmission')" min-width="130">
          <template #header>
            <span>{{ t('auditList.colTotalEmission') }}<TermTip term="tco2e" /></span>
          </template>
          <template #default="{ row }">{{ formatNumber(row.totalEmission) }} tCO2e</template>
        </el-table-column>
        <el-table-column prop="statusText" :label="t('auditList.colStatus')" min-width="100">
          <template #default="{ row }">
            <StatusTag domain="report" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column :label="t('auditList.colSignature')" min-width="110" align="center">
          <template #header>
            <span>{{ t('auditList.colSignature') }}<TermTip term="verifySignature" /></span>
          </template>
          <template #default="{ row }">
            <el-tag v-if="row.signatureValid === true" type="success"><el-icon><CircleCheck /></el-icon> {{ t('auditList.sigValid') }}</el-tag>
            <el-tag v-else-if="row.signatureValid === false" type="danger"><el-icon><CircleClose /></el-icon> {{ t('auditList.sigInvalid') }}</el-tag>
            <el-tag v-else-if="row.signatureData" type="info"><el-icon><Key /></el-icon> {{ t('auditList.sigSigned') }}</el-tag>
            <el-tag v-else type="info">— {{ t('auditList.sigUnsigned') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('auditList.colCreateTime')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.reviewedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('auditList.colOperation')" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canReviewRow(row)"
              link
              type="primary"
              @click="openReviewDialog(row)"
            >
              {{ t('auditList.reviewAction') }}
            </el-button>
            <span v-else class="empty-op">-</span>
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

    <el-dialog v-model="reviewDialogVisible" :title="t('auditList.dialogTitle')" width="600px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item :label="t('auditList.emissionDetail')">
          <EmissionDataPanel :data="reviewingRow?.emissionData" />
        </el-form-item>
        <el-form-item :label="t('auditList.labelSignature')">
          <el-tag
            :type="rowSignatureValid === false ? 'danger' : rowSignatureValid === true ? 'success' : 'info'"
          >
            <template v-if="rowSignatureValid === true">{{ t('auditList.sigValid') }}</template>
            <template v-else-if="rowSignatureValid === false">{{ t('auditList.sigInvalid') }}</template>
            <template v-else-if="rowHasSignature">{{ t('auditList.sigSigned') }}</template>
            <template v-else>{{ t('auditList.sigUnsigned') }}</template>
          </el-tag>
          <span
            v-if="rowSignatureValid === false"
            class="signature-warn"
          >{{ t('auditList.invalidApproveHint') }}</span>
        </el-form-item>

        <!-- 佐证附件区块：审核员必须能看见并下载企业上传的凭据，再决定通过/驳回 -->
        <el-form-item :label="t('auditList.labelAttachments')">
          <div class="review-attachments" data-testid="review-attachments">
            <div v-if="reviewAttachments.length === 0" class="review-attachments__empty">
              {{ t('auditList.attachmentsEmpty') }}
            </div>
            <ul v-else class="review-attachments__list">
              <li v-for="(item, index) in reviewAttachments" :key="item.objectName || index" class="review-attachments__item">
                <div class="review-attachments__meta">
                  <span class="review-attachments__name" :title="item.name">{{ item.name }}</span>
                  <span class="review-attachments__size">{{ formatAttachmentSize(item.size) }}</span>
                  <span v-if="item.hash" class="review-attachments__hash" :title="item.hash">
                    SHA-256: {{ item.hash.slice(0, 12) }}…{{ item.hash.slice(-8) }}
                  </span>
                </div>
                <el-button
                  link
                  type="primary"
                  :loading="attachmentDownloading === item.objectName"
                  :disabled="!item.downloadable"
                  @click="onDownloadAttachment(item)"
                >
                  {{ t('auditList.attachmentDownload') }}
                </el-button>
              </li>
            </ul>
            <p v-if="reviewAttachments.some((item) => !item.downloadable)" class="review-attachments__warn">
              {{ t('auditList.attachmentUnresolvable') }}
            </p>
          </div>
        </el-form-item>

        <!-- 规则预检卡片：确定性事实核对，非 AI 模型结论 -->
        <div v-if="rulePrecheck" class="ai-preaudit-card" data-testid="rule-precheck">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-chart-1)" :size="15"><Cpu /></el-icon>
                <strong style="color: var(--color-text); font-size: 13.5px;">{{ t('auditList.rulePrecheckTitle') }}</strong>
              </div>
              <el-tag :type="rulePrecheck.statusType" size="small">{{ rulePrecheck.statusText }}</el-tag>
            </div>
            <el-button size="small" type="primary" link @click="applyRulePrecheckComment">
              {{ t('auditList.btnAdoptRuleComment') }}
            </el-button>
          </div>
          <p class="rule-precheck-note">{{ t('auditList.rulePrecheckNote') }}</p>
          <div style="font-size: 12px; color: var(--color-text-muted); line-height: 1.6;">
            <div v-for="(f, idx) in rulePrecheck.findings" :key="idx" style="margin-bottom: 2px;">
              • {{ f }}
            </div>
          </div>
        </div>

        <el-form-item :label="t('auditList.labelResult')">
          <el-radio-group v-model="reviewForm.approved">
            <el-radio :value="true">{{ t('auditList.approve') }}</el-radio>
            <el-radio :value="false">{{ t('auditList.reject') }}</el-radio>
          </el-radio-group>
          <div v-if="!reviewForm.approved" class="reject-credit-notice">
            <span style="display: inline-flex; align-items: center; gap: 4px;">
              <el-icon color="var(--color-status-warning)"><Warning /></el-icon>
              {{ t('quickReasons.creditNotice') }}
            </span>
          </div>
        </el-form-item>

        <el-form-item :label="t('auditList.labelComment')">
          <div v-if="!reviewForm.approved" class="quick-reasons-panel">
            <span class="quick-reasons-title">{{ t('quickReasons.title') }}:</span>
            <div class="quick-reasons-tags">
              <el-tag
                v-for="rKey in quickRejectReasons"
                :key="rKey"
                size="small"
                class="quick-reason-tag"
                effect="plain"
                type="warning"
                @click="applyQuickRejectReason(rKey)"
              >
                + {{ t(rKey) }}
              </el-tag>
            </div>
          </div>
          <el-input
            v-model="reviewForm.comment"
            type="textarea"
            :rows="4"
            :placeholder="t('auditList.enterComment')"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="closeReviewDialog" :disabled="reviewSubmitting">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="reviewSubmitting"
          @click="submitReview"
        >{{ t('common.submit') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.reviewer-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: var(--fs-sub);
  color: var(--pen-muted);
}

.identity-line,
.identity-sub {
  color: var(--pen-muted);
  font-size: var(--fs-sub);
}

.empty-op {
  color: var(--pen-faint);
}

.signature-warn {
  margin-left: var(--space-2-5);
  color: var(--pen-danger);
  font-size: var(--fs-sub);
}

.reject-credit-notice {
  margin-top: var(--space-1-5);
  font-size: var(--fs-sub);
  color: var(--pen-danger);
}

.quick-reasons-panel {
  width: 100%;
  margin-bottom: var(--space-2);
}

.quick-reasons-title {
  font-size: var(--fs-sub);
  color: var(--pen-muted);
  margin-right: var(--space-2);
}

.quick-reasons-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: var(--space-1-5);
  margin-top: var(--space-1);
}

/* ---------- 佐证附件区块 ---------- */
.review-attachments {
  width: 100%;
}

.review-attachments__empty {
  color: var(--pen-muted);
  font-size: var(--fs-sub);
}

.review-attachments__list {
  list-style: none;
  margin: 0;
  padding: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--space-1-5);
}

.review-attachments__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2-5);
  background: var(--pen-raised);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
}

.review-attachments__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.review-attachments__name {
  color: var(--color-text);
  font-size: var(--fs-sub);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-attachments__size,
.review-attachments__hash {
  color: var(--pen-muted);
  font-size: var(--fs-sub);
}

.review-attachments__warn {
  margin: var(--space-1-5) 0 0;
  color: var(--pen-danger);
  font-size: var(--fs-sub);
}

.ai-preaudit-card {
  margin: 0 0 var(--space-4) 110px;
  padding: var(--space-3);
  background: var(--pen-raised);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
}

.rule-precheck-note {
  margin: 0 0 var(--space-2);
  font-size: var(--fs-sub);
  color: var(--pen-muted);
}

.quick-reason-tag {
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-reason-tag:hover {
  transform: translateY(-1px);
}
</style>
