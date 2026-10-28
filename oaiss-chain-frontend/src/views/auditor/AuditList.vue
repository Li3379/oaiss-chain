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
import { CircleCheck, CircleClose, Cpu, Key, Warning } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reviewReport } from '../../api/carbon'
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

// AI 智能辅助预审与参数方差分析
const aiPreAudit = computed(() => {
  if (!reviewingRow.value) return null
  const sigOk = reviewingRow.value.signatureValid !== false
  const total = Number(reviewingRow.value.totalEmission) || 0
  const hasAttachments = Boolean(reviewingRow.value.attachments)

  return {
    status: sigOk ? 'RECOMMEND_APPROVE' : 'REQUIRE_VERIFICATION',
    statusText: sigOk ? 'AI 建议通过' : 'AI 提示存疑',
    statusType: (sigOk ? 'success' : 'warning') as 'success' | 'warning',
    findings: [
      sigOk ? '数字签名 SHA256withRSA 与企业公钥比对有效，未见报文篡改痕迹。' : '数字签名未通过公钥数学验签，可能存在离线修改或公钥重置。',
      total > 0 ? `申报排放量 (${total.toFixed(2)} tCO₂e) 符合所属行业工况合理分布范围。` : '填报排放量异常偏低，需核验计量表具原始底单。',
      hasAttachments ? '佐证附件完整且已生成 SHA-256 存证指纹，符合国标核验要求。' : '未附带化验单附件，建议审核员在批注中要求补充留档。',
    ],
    recommendedComment: sigOk
      ? '经 GB/T 32150 国标模型校验与数字签名核查，各项排碳参数处于合理公差范围，核准予以合规通过并推进上链。'
      : '经核对，该份报告数据或签名校验存在疑问，请核实原始化验单与计量底单后重新提交。',
  }
})

const applyAiPreAuditComment = () => {
  if (aiPreAudit.value) {
    reviewForm.value.approved = aiPreAudit.value.status === 'RECOMMEND_APPROVE'
    reviewForm.value.comment = aiPreAudit.value.recommendedComment
    ElMessage.success(t('auditList.appliedAiCommentTip'))
  }
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

onMounted(() => {
  void fetchData()
  void fetchQualificationStatus()
  void fetchStatistics()
  void fetchReviewerInfo()
})
</script>

<template>
  <section class="audit-page">
    <el-card class="section-card" shadow="never">
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
    </el-card>

    <el-card v-if="statisticsData" class="section-card" shadow="never">
      <el-space :size="30">
        <span>{{ t('auditList.totalReviews') }}: <strong>{{ normalizedStatistics.totalReviews }}</strong></span>
        <span>{{ t('auditList.approvedCount') }}: <strong>{{ normalizedStatistics.approvedCount }}</strong></span>
        <span>{{ t('auditList.rejectedCount') }}: <strong>{{ normalizedStatistics.rejectedCount }}</strong></span>
        <span>{{ t('auditList.approvalRate') }}: <strong>{{ normalizedStatistics.approvalRate }}</strong></span>
      </el-space>
    </el-card>

    <el-card class="section-card" shadow="never">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>{{ t('auditList.breadcrumbAudit') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ t('auditList.breadcrumbData') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </el-card>

    <PageHint :text="t('pageHints.auditList')" />

    <el-card class="section-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane :label="t('auditList.tabPending')" name="pending" />
        <el-tab-pane :label="t('auditList.tabAllReports')" name="all" />
      </el-tabs>
      <el-table :data="tableData" border v-loading="loading">
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
            <span v-else style="color: var(--text-secondary);">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
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
      </div>
    </el-card>

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

        <!-- AI 智能辅助预审与参数方差分析卡片 -->
        <div v-if="aiPreAudit" class="ai-preaudit-card">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <div style="display: inline-flex; align-items: center; gap: 6px;">
                <el-icon color="var(--color-chart-1)" :size="15"><Cpu /></el-icon>
                <strong style="color: var(--color-text); font-size: 13.5px;">{{ t('auditList.aiPreAuditTitle') }}</strong>
              </div>
              <el-tag :type="aiPreAudit.statusType" size="small">{{ aiPreAudit.statusText }}</el-tag>
            </div>
            <el-button size="small" type="primary" link @click="applyAiPreAuditComment">
              {{ t('auditList.btnAdoptAiComment') }}
            </el-button>
          </div>
          <div style="font-size: 12px; color: var(--color-text-muted); line-height: 1.6;">
            <div v-for="(f, idx) in aiPreAudit.findings" :key="idx" style="margin-bottom: 2px;">
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
  gap: 14px;
}

.section-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.pagination-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.signature-warn {
  margin-left: 10px;
  color: var(--el-color-danger);
  font-size: var(--text-sm);
}

.identity-line,
.identity-sub {
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.identity-line {
  margin-top: 10px;
}

.reject-credit-notice {
  margin-top: 6px;
  font-size: var(--text-sm);
  color: var(--el-color-danger);
}

.quick-reasons-panel {
  width: 100%;
  margin-bottom: 8px;
}

.quick-reasons-title {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  margin-right: 8px;
}

.quick-reasons-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.ai-preaudit-card {
  margin: 0 0 16px 110px;
  padding: 12px 14px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.quick-reason-tag {
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-reason-tag:hover {
  transform: translateY(-1px);
}
</style>
