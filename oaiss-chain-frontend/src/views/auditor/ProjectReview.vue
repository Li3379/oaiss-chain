<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：核查员工作台 - 碳中和项目立项审核、现场核证与最终认证 (Project Review)
 * 【路由地址】：/auditor/project/review
 * 【访问权限】：ROLE_REVIEWER (第三方核查员) / ROLE_ADMIN
 * 【界面可见功能】：
 *   1. 待审核项目队列：企业提交立项后（status=1）在此通过或驳回，驳回必须填写意见
 *   2. 待第三方核证项目工单池：分页展示进入核证周期的减排/碳汇项目清单
 *   3. 现场核证结论录入弹窗：录入实测核减排量、核证报告文号与专家意见
 *   4. 违规造假信誉扣分联动：核查驳回时，支持向企业下发失信扣分违规处罚
 *   5. 已核证待认证项目：录入证书编号完成最终认证（certStatus 1 -> 2）
 * 【对应后端 Controller & API】：
 *   - CarbonNeutralProjectController:
 *     * GET  /api/v1/carbon-neutral/search               -> getProjects() 按 status 列出待审核/已完成项目
 *     * POST /api/v1/carbon-neutral/{id}/review          -> reviewProject() 通过或驳回立项
 *     * GET  /api/v1/carbon-neutral/pending-verification -> getPendingVerification() 查询待核证项目池
 *     * POST /api/v1/carbon-neutral/verify               -> verifyProject() 提交减排量核证结论
 *     * POST /api/v1/carbon-neutral/{id}/certify         -> completeCertification() 完成最终认证
 *   - CreditScoreController:
 *     * POST /api/v1/credit/deduct                      -> deductPoints() 执行违规企业信用扣分
 * ==============================================================================
 */
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Clock, Odometer, TrendCharts } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  getPendingVerification,
  getProjects,
  reviewProject,
  verifyProject,
  completeCertification,
} from '../../api/carbonNeutral'
import { deductPoints } from '../../api/credit'
import type { CarbonNeutralProjectResponse } from '../../types'
import { formatDateTime } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import EmptyHint from '../../components/EmptyHint.vue'

const { t } = useI18n()

/** 与后端 CarbonNeutralProjectService 状态常量对齐 */
const PROJECT_STATUS_PENDING = 1
const PROJECT_STATUS_COMPLETED = 4
const VERIFY_STATUS_PENDING = 1
const VERIFY_STATUS_VERIFIED = 2
const VERIFY_STATUS_FAILED = 3
const CERT_STATUS_PENDING = 1

type ProjectReviewRow = CarbonNeutralProjectResponse & {
  enterpriseId?: number
}

type BackendErrorPayload = {
  response?: {
    data?: {
      data?: unknown
      message?: string
    }
  }
  message?: string
}

type ActiveTab = 'review' | 'verify'

const activeTab = ref<ActiveTab>('review')

// ==================== 待核证工单池 ====================
const tableData = ref<ProjectReviewRow[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// ==================== 待审核项目队列 ====================
const reviewRows = ref<ProjectReviewRow[]>([])
const reviewLoading = ref(false)
const reviewPage = ref(1)
const reviewPageSize = ref(10)
const reviewTotal = ref(0)
const reviewActionId = ref<number | null>(null)

// ==================== 已核证待认证项目 ====================
const certRows = ref<ProjectReviewRow[]>([])
const certLoading = ref(false)
const certPage = ref(1)
const certPageSize = ref(10)
/** 服务端 status=4 的分页总数：只用于 el-pagination 翻页，不代表待认证数量 */
const certPageTotal = ref(0)
/** 前端过滤后真正待认证（verified + certStatus=1）的行数：用于「共 N 条」与统计卡 */
const certTotal = ref(0)
const certActionId = ref<number | null>(null)

const verifyDialogVisible = ref(false)
const verifyForm = ref({
  projectId: null as number | null,
  verifiedReduction: '',
  verificationReport: '',
  monitoringData: '',
  remark: '',
})
const verifyLoading = ref(false)
const verifyErrorMessage = ref('')

const deductDialogVisible = ref(false)
const deductForm = ref({ enterpriseId: null as number | null, eventType: 1, description: '' })
const deductLoading = ref(false)

const getBackendErrorMessage = (error: unknown) => {
  const candidate = error as BackendErrorPayload | undefined
  const payload = candidate?.response?.data?.data

  if (Array.isArray(payload) && typeof payload[0] === 'string' && payload[0].trim()) {
    return payload[0]
  }

  if (typeof candidate?.response?.data?.message === 'string' && candidate.response.data.message.trim()) {
    return candidate.response.data.message
  }

  if (typeof candidate?.message === 'string' && candidate.message.trim()) {
    return candidate.message
  }

  return ''
}

/** 统一把后端数值字段归一为 number（列表接口偶发字符串型状态） */
const normalizeRows = (rows: CarbonNeutralProjectResponse[]): ProjectReviewRow[] =>
  rows.map((row) => ({
    ...row,
    status: Number(row.status ?? 0),
    verificationStatus: Number(row.verificationStatus ?? 0),
    certStatus: Number(row.certStatus ?? 0),
  }))

// ==================== 待核证工单池 ====================
const fetchData = async () => {
  loading.value = true
  try {
    const response = await getPendingVerification({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
    tableData.value = normalizeRows(response.items)
    total.value = response.total
  } catch {
    ElMessage.error(t('projectReview.loadFailed'))
  } finally {
    loading.value = false
  }
}

// ==================== 待审核队列（status=1） ====================
const fetchReviewQueue = async () => {
  reviewLoading.value = true
  try {
    const response = await getProjects({
      status: PROJECT_STATUS_PENDING,
      pageNum: reviewPage.value,
      pageSize: reviewPageSize.value,
    })
    reviewRows.value = normalizeRows(response.items)
    reviewTotal.value = response.total
  } catch {
    ElMessage.error(t('projectReview.loadReviewFailed'))
  } finally {
    reviewLoading.value = false
  }
}

/**
 * 已核证待认证项目：后端没有按 certStatus 过滤的查询端点，
 * 因此按 status=4（已完成）分页拉取，再在前端筛出 verificationStatus=2 且 certStatus=1 的行。
 *
 * 口径必须分开：`certPageTotal` 是服务端 status=4 的总数（含尚未申请认证的项目），
 * 只喂给分页器；`certTotal` 才是真正待认证的数量，用于「共 N 条」与统计卡。
 * 若两者混用，会出现「共 16 条」但表格「暂无数据」的自相矛盾。
 */
const fetchCertQueue = async () => {
  certLoading.value = true
  try {
    const response = await getProjects({
      status: PROJECT_STATUS_COMPLETED,
      pageNum: certPage.value,
      pageSize: certPageSize.value,
    })
    certRows.value = normalizeRows(response.items).filter(
      (row) => row.verificationStatus === VERIFY_STATUS_VERIFIED && row.certStatus === CERT_STATUS_PENDING,
    )
    certPageTotal.value = response.total
    certTotal.value = certRows.value.length
  } catch {
    ElMessage.error(t('projectReview.loadCertFailed'))
  } finally {
    certLoading.value = false
  }
}

const onTabChange = (name: string | number) => {
  if (name === 'review') fetchReviewQueue()
  else {
    fetchData()
    fetchCertQueue()
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

const onReviewSizeChange = (size: number) => {
  reviewPageSize.value = size
  reviewPage.value = 1
  fetchReviewQueue()
}

const onReviewCurrentChange = (page: number) => {
  reviewPage.value = page
  fetchReviewQueue()
}

const onCertSizeChange = (size: number) => {
  certPageSize.value = size
  certPage.value = 1
  fetchCertQueue()
}

const onCertCurrentChange = (page: number) => {
  certPage.value = page
  fetchCertQueue()
}

// ==================== 立项审核 ====================
const approveProject = async (row: ProjectReviewRow) => {
  try {
    await ElMessageBox.confirm(
      t('projectReview.confirmApprove', { name: row.projectName }),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch {
    return
  }

  reviewActionId.value = row.id
  try {
    await reviewProject(row.id, { approved: true, comment: '' })
    ElMessage.success(t('projectReview.reviewApproved'))
    await fetchReviewQueue()
  } catch (error: unknown) {
    ElMessage.error(getBackendErrorMessage(error) || t('projectReview.reviewFailed'))
  } finally {
    reviewActionId.value = null
  }
}

/** 驳回必须填写意见：后端把 reviewComment 原样回写给企业，并置为 6 已拒绝 */
const rejectProject = async (row: ProjectReviewRow) => {
  let comment: string
  try {
    const { value } = await ElMessageBox.prompt(
      t('projectReview.enterRejectComment'),
      t('projectReview.rejectDialogTitle'),
      {
        confirmButtonText: t('projectReview.reject'),
        cancelButtonText: t('common.cancel'),
        inputType: 'textarea',
        inputPattern: /\S+/,
        inputErrorMessage: t('projectReview.rejectCommentRequired'),
      },
    )
    comment = value.trim()
  } catch {
    return
  }
  if (!comment) {
    ElMessage.warning(t('projectReview.rejectCommentRequired'))
    return
  }

  reviewActionId.value = row.id
  try {
    await reviewProject(row.id, { approved: false, comment })
    ElMessage.success(t('projectReview.reviewRejected'))
    await fetchReviewQueue()
  } catch (error: unknown) {
    ElMessage.error(getBackendErrorMessage(error) || t('projectReview.reviewFailed'))
  } finally {
    reviewActionId.value = null
  }
}

// ==================== 核证 ====================
const openVerifyDialog = (row: ProjectReviewRow) => {
  verifyErrorMessage.value = ''
  verifyForm.value = {
    projectId: row.id,
    verifiedReduction: String(row.expectedReduction ?? ''),
    verificationReport: '',
    monitoringData: row.monitoringData || '',
    remark: '',
  }
  verifyDialogVisible.value = true
}

const submitVerify = async () => {
  verifyErrorMessage.value = ''
  const verifiedReduction = Number(verifyForm.value.verifiedReduction)
  if (!Number.isFinite(verifiedReduction) || verifiedReduction <= 0) {
    const invalidMessage = t('projectReview.verifiedReductionInvalid')
    verifyErrorMessage.value = invalidMessage === 'projectReview.verifiedReductionInvalid'
      ? `${t('projectReview.labelVerifiedReduction')} > 0`
      : invalidMessage
    ElMessage.warning(verifyErrorMessage.value)
    return
  }

  verifyLoading.value = true
  try {
    await verifyProject({
      projectId: verifyForm.value.projectId!,
      verifiedReduction,
      verificationReport: verifyForm.value.verificationReport || undefined,
      monitoringData: verifyForm.value.monitoringData || undefined,
      remark: verifyForm.value.remark || undefined,
    })
    ElMessage.success(t('projectReview.verifyPassed'))
    verifyDialogVisible.value = false
    fetchData()
  } catch (error: unknown) {
    const backendMessage = getBackendErrorMessage(error)
    verifyErrorMessage.value = backendMessage || t('projectReview.verifySubmitFailed')
    ElMessage.error(backendMessage || t('projectReview.verifySubmitFailed'))
  } finally {
    verifyLoading.value = false
  }
}

// ==================== 完成认证 ====================
const certificationAction = async (row: ProjectReviewRow) => {
  let certNo: string
  try {
    const { value } = await ElMessageBox.prompt(
      t('projectReview.enterCertNo'),
      t('projectReview.certDialogTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputValue: row.certNo || '',
        inputPattern: /\S+/,
        inputErrorMessage: t('projectReview.certNoRequired'),
      },
    )
    certNo = value.trim()
  } catch {
    return
  }
  if (!certNo) {
    ElMessage.warning(t('projectReview.certNoRequired'))
    return
  }

  certActionId.value = row.id
  try {
    await completeCertification(row.id, { certNo })
    ElMessage.success(t('projectReview.certificationCompleted'))
    await fetchCertQueue()
  } catch (error: unknown) {
    ElMessage.error(getBackendErrorMessage(error) || t('projectReview.certificationFailed'))
  } finally {
    certActionId.value = null
  }
}

// ==================== 信用扣分 ====================
const openDeductDialog = (row: ProjectReviewRow) => {
  const resolvedEnterpriseId = Number(row.enterpriseId ?? row.ownerId ?? 0)
  if (!Number.isFinite(resolvedEnterpriseId) || resolvedEnterpriseId <= 0) {
    ElMessage.error(t('projectReview.deductFailed'))
    return
  }
  deductForm.value = { enterpriseId: resolvedEnterpriseId, eventType: 1, description: '' }
  deductDialogVisible.value = true
}

const submitDeduct = async () => {
  const description = deductForm.value.description.trim()
  if (!description) {
    ElMessage.warning(t('projectReview.enterDescription'))
    return
  }

  deductLoading.value = true
  try {
    await deductPoints({
      enterpriseId: deductForm.value.enterpriseId!,
      eventType: deductForm.value.eventType,
      description,
    })
    ElMessage.success(t('projectReview.deductSuccess'))
    deductDialogVisible.value = false
  } catch (error: unknown) {
    const backendMessage = getBackendErrorMessage(error)
    ElMessage.error(backendMessage || t('projectReview.deductFailed'))
  } finally {
    deductLoading.value = false
  }
}

const STATUS_MAP: Record<number, string> = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'primary',
  4: 'success',
  5: 'danger',
  6: 'danger',
}

const VERIFICATION_STATUS_MAP: Record<number, string> = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'danger',
}

const getStatusTag = (status: number) => {
  return STATUS_MAP[status] || 'info'
}

const getVerificationTag = (status: number) => {
  return VERIFICATION_STATUS_MAP[status] || 'info'
}

const canVerify = (row: ProjectReviewRow) => {
  return Number(row.verificationStatus) === VERIFY_STATUS_PENDING
}

const canDeduct = (row: ProjectReviewRow) => {
  return Number(row.verificationStatus) === VERIFY_STATUS_PENDING
}

/**
 * 完成认证：后端 completeCertification 校验 certStatus===PENDING(1)。
 * 仅 verificationStatus=2 还不够——企业必须先「申请认证」，否则必然报 3003「项目不在待认证状态」。
 */
const canCompleteCertification = (row: ProjectReviewRow) => {
  return Number(row.verificationStatus) === VERIFY_STATUS_VERIFIED
    && Number(row.certStatus) === CERT_STATUS_PENDING
}

const canReview = (row: ProjectReviewRow) => row.status === PROJECT_STATUS_PENDING

const hasRows = computed(() => tableData.value.length > 0)
const hasReviewRows = computed(() => reviewRows.value.length > 0)
const hasCertRows = computed(() => certRows.value.length > 0)

const activeLoading = computed(() => (activeTab.value === 'review' ? reviewLoading.value : loading.value))

/** 首屏统计条：全部由已加载的本页数据推导，不新增接口请求 */
const overviewStats = computed(() => {
  if (activeTab.value === 'review') {
    const rows = reviewRows.value
    const expectedTotal = rows.reduce((sum, row) => sum + (Number(row.expectedReduction) || 0), 0)
    return [
      {
        label: t('projectReview.statReviewTotal'),
        value: reviewTotal.value,
        unit: t('projectReview.statUnitProjects'),
        delta: t('projectReview.statReviewTotalHint', { count: rows.length }),
        tone: 'neu' as const,
        icon: Odometer,
      },
      {
        label: t('projectReview.statReviewOnPage'),
        value: rows.filter((row) => row.status === PROJECT_STATUS_PENDING).length,
        unit: t('projectReview.statUnitProjects'),
        delta: t('projectReview.statReviewOnPageHint'),
        tone: 'neu' as const,
        icon: Clock,
      },
      {
        label: t('projectReview.statReviewReduction'),
        value: Number(expectedTotal.toFixed(1)),
        unit: 'tCO2e',
        delta: t('projectReview.statReviewReductionHint'),
        tone: 'neu' as const,
        icon: TrendCharts,
      },
      {
        label: t('projectReview.statCertPending'),
        value: certTotal.value,
        unit: t('projectReview.statUnitProjects'),
        delta: t('projectReview.statCertPendingHint'),
        tone: 'neu' as const,
        icon: CircleCheck,
      },
    ]
  }

  const rows = tableData.value
  const awaiting = rows.filter((row) => Number(row.verificationStatus) === VERIFY_STATUS_PENDING).length
  const verified = rows.filter((row) => Number(row.verificationStatus) === VERIFY_STATUS_VERIFIED).length
  const rejected = rows.filter((row) => Number(row.verificationStatus) === VERIFY_STATUS_FAILED).length
  const expectedTotal = rows.reduce((sum, row) => sum + (Number(row.expectedReduction) || 0), 0)

  return [
    {
      label: t('projectReview.statTotal'),
      value: total.value,
      unit: t('projectReview.statUnitProjects'),
      delta: t('projectReview.statTotalHint', { count: rows.length }),
      tone: 'neu' as const,
      icon: Odometer,
    },
    {
      label: t('projectReview.statAwaiting'),
      value: awaiting,
      unit: t('projectReview.statUnitProjects'),
      delta: t('projectReview.statAwaitingHint'),
      tone: awaiting > 0 ? ('neu' as const) : ('pos' as const),
      icon: Clock,
    },
    {
      label: t('projectReview.statVerified'),
      value: verified,
      unit: t('projectReview.statUnitProjects'),
      delta: t('projectReview.statVerifiedHint', { rejected }),
      tone: rejected > 0 ? ('neg' as const) : ('pos' as const),
      icon: CircleCheck,
    },
    {
      label: t('projectReview.statExpectedReduction'),
      value: Number(expectedTotal.toFixed(1)),
      unit: 'tCO2e',
      delta: t('projectReview.statExpectedReductionHint'),
      tone: 'neu' as const,
      icon: TrendCharts,
    },
  ]
})

onMounted(() => {
  // 两条队列都预加载：Tab 标签与统计条需要在切页前就有真实数量
  fetchReviewQueue()
  fetchData()
  fetchCertQueue()
})
</script>

<template>
  <section class="review-page pen-enter">
    <PageHeader
      :title="t('menu.projectReview')"
      :description="t('projectReview.pageDescription')"
      :crumbs="[t('projectReview.breadcrumbAudit'), t('projectReview.breadcrumbProject')]"
    />

    <PageHint :text="t('pageHints.projectReview')" />

    <StatStrip :items="overviewStats" :loading="activeLoading" />

    <el-tabs v-model="activeTab" class="review-tabs" @tab-change="onTabChange">
      <!-- ==================== 待审核 ==================== -->
      <el-tab-pane :label="t('projectReview.tabReview')" name="review">
        <DataPanel
          :title="t('projectReview.queueReview')"
          :meta="t('projectReview.panelMeta', { total: reviewTotal })"
        >
          <p class="panel-note">{{ t('projectReview.queueReviewDesc') }}</p>

          <el-table :data="reviewRows" v-loading="reviewLoading">
            <el-table-column prop="projectNo" :label="t('projectReview.colProjectNo')" min-width="150" />
            <el-table-column prop="projectName" :label="t('projectReview.colProjectName')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="ownerName" :label="t('projectReview.colEnterpriseName')" min-width="160" />
            <el-table-column prop="projectTypeName" :label="t('projectReview.colProjectType')" min-width="120" />
            <el-table-column prop="status" :label="t('common.status')" min-width="110">
              <template #default="{ row }">
                <el-tag :type="getStatusTag(row.status)">{{ row.statusText || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expectedReduction" :label="t('projectReview.colExpectedReduction')" min-width="130" />
            <el-table-column prop="createdAt" :label="t('common.createTime')" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="canReview(row)">
                  <el-button
                    link
                    type="success"
                    :loading="reviewActionId === row.id"
                    @click="approveProject(row)"
                  >{{ t('projectReview.approve') }}</el-button>
                  <el-button
                    link
                    type="danger"
                    :disabled="reviewActionId === row.id"
                    @click="rejectProject(row)"
                  >{{ t('projectReview.reject') }}</el-button>
                </template>
                <span v-else class="empty-op">-</span>
              </template>
            </el-table-column>
          </el-table>

          <EmptyHint v-if="!reviewLoading && !hasReviewRows" :description="t('projectReview.emptyReviewText')" />

          <template #footer>
            <el-pagination
              v-model:current-page="reviewPage"
              v-model:page-size="reviewPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="reviewTotal"
              @size-change="onReviewSizeChange"
              @current-change="onReviewCurrentChange"
            />
          </template>
        </DataPanel>
      </el-tab-pane>

      <!-- ==================== 待核证 ==================== -->
      <el-tab-pane :label="t('projectReview.tabVerification')" name="verify">
        <div class="verify-stack">
          <DataPanel
            :title="t('projectReview.queueVerification')"
            :meta="t('projectReview.panelMeta', { total })"
          >
            <p class="panel-note">{{ t('projectReview.queueVerificationDesc') }}</p>

            <el-table :data="tableData" v-loading="loading">
              <el-table-column prop="projectName" :label="t('projectReview.colProjectName')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="ownerName" :label="t('projectReview.colEnterpriseName')" min-width="160" />
              <el-table-column prop="projectTypeName" :label="t('projectReview.colProjectType')" min-width="120" />
              <el-table-column prop="status" :label="t('common.status')" min-width="120">
                <template #default="{ row }">
                  <el-tag :type="getStatusTag(row.status)">{{ row.statusText || row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="verificationStatus" :label="t('projectReview.colVerificationStatus')" min-width="140">
                <template #default="{ row }">
                  <el-tag :type="getVerificationTag(row.verificationStatus)">{{ row.verificationStatusText || row.verificationStatus }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="expectedReduction" :label="t('projectReview.colExpectedReduction')" min-width="130" />
              <el-table-column prop="createdAt" :label="t('common.createTime')" min-width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column :label="t('common.operation')" width="280" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="canVerify(row)" link type="success" @click="openVerifyDialog(row)">{{ t('projectReview.verify') }}</el-button>
                  <el-button v-if="canDeduct(row)" link type="warning" @click="openDeductDialog(row)">{{ t('projectReview.deductCredit') }}</el-button>
                  <span v-if="!canVerify(row) && !canDeduct(row)" class="empty-op">-</span>
                </template>
              </el-table-column>
            </el-table>

            <EmptyHint v-if="!loading && !hasRows" :description="t('projectReview.emptyText')">
              <p class="empty-tip">{{ t('nextSteps.emptyProjectsTip') }}</p>
            </EmptyHint>

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

          <!-- 已核证待认证：完成认证后 certStatus 才真正到达 2 -->
          <DataPanel
            :title="t('projectReview.queueCertification')"
            :meta="t('projectReview.panelMetaCert', { total: certTotal })"
          >
            <p class="panel-note">{{ t('projectReview.queueCertificationDesc') }}</p>

            <el-table :data="certRows" v-loading="certLoading">
              <el-table-column prop="projectNo" :label="t('projectReview.colProjectNo')" min-width="150" />
              <el-table-column prop="projectName" :label="t('projectReview.colProjectName')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="ownerName" :label="t('projectReview.colEnterpriseName')" min-width="160" />
              <el-table-column prop="verificationStatus" :label="t('projectReview.colVerificationStatus')" min-width="130">
                <template #default="{ row }">
                  <el-tag :type="getVerificationTag(row.verificationStatus)">{{ row.verificationStatusText || row.verificationStatus }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="certOrg" :label="t('projectReview.colCertOrg')" min-width="150" />
              <el-table-column prop="createdAt" :label="t('common.createTime')" min-width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column :label="t('common.operation')" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="canCompleteCertification(row)"
                    link
                    type="success"
                    :loading="certActionId === row.id"
                    @click="certificationAction(row)"
                  >{{ t('projectReview.completeCertification') }}</el-button>
                  <span v-else class="empty-op">-</span>
                </template>
              </el-table-column>
            </el-table>

            <EmptyHint v-if="!certLoading && !hasCertRows" :description="t('projectReview.emptyCertText')" />

            <template #footer>
              <!--
                分页器上的 total 是「已完成项目」池大小（翻页需要），
                与本面板「待认证」口径不同，因此不显示 total 文案，避免出现
                「共 16 条」却「暂无数据」的误导。
              -->
              <el-pagination
                v-model:current-page="certPage"
                v-model:page-size="certPageSize"
                background
                :page-sizes="[10, 20, 50]"
                layout="sizes, prev, pager, next, jumper"
                :total="certPageTotal"
                @size-change="onCertSizeChange"
                @current-change="onCertCurrentChange"
              />
            </template>
          </DataPanel>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Verify Dialog -->
    <el-dialog v-model="verifyDialogVisible" :title="t('projectReview.verifyDialogTitle')" width="600px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item :label="t('projectReview.labelVerifiedReduction')">
          <el-input v-model="verifyForm.verifiedReduction" />
        </el-form-item>
        <el-form-item :label="t('projectReview.labelVerificationReport')">
          <el-input v-model="verifyForm.verificationReport" type="textarea" :rows="3" :placeholder="t('projectReview.enterVerificationReport')" />
        </el-form-item>
        <el-form-item :label="t('projectReview.labelMonitoringData')">
          <el-input v-model="verifyForm.monitoringData" type="textarea" :rows="3" :placeholder="t('projectReview.enterMonitoringData')" />
        </el-form-item>
        <el-form-item :label="t('projectReview.labelRemark')">
          <el-input v-model="verifyForm.remark" type="textarea" :rows="3" :placeholder="t('projectReview.enterRemark')" />
        </el-form-item>
        <div v-if="verifyErrorMessage" class="form-error-text">{{ verifyErrorMessage }}</div>
      </el-form>
      <template #footer>
        <el-button @click="verifyDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="verifyLoading" @click="submitVerify">{{ t('common.submit') }}</el-button>
      </template>
    </el-dialog>

    <!-- Credit Deduction Dialog -->
    <el-dialog v-model="deductDialogVisible" :title="t('projectReview.deductDialogTitle')" width="500px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item :label="t('projectReview.labelEventType')">
          <el-select v-model="deductForm.eventType">
            <el-option :label="t('projectReview.eventTypeViolation')" :value="1" />
            <el-option :label="t('projectReview.eventTypeFraud')" :value="2" />
            <el-option :label="t('projectReview.eventTypeOther')" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('projectReview.labelDescription')">
          <el-input v-model="deductForm.description" type="textarea" :rows="3" :placeholder="t('projectReview.enterDescription')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deductDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="deductLoading" @click="submitDeduct">{{ t('projectReview.confirmDeduct') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.review-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.review-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--space-4);
}

.verify-stack {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.panel-note {
  margin: 0;
  padding: var(--space-3) var(--space-4) 0;
  color: var(--pen-muted);
  font-size: var(--fs-sub);
  line-height: 1.6;
}

.empty-op {
  color: var(--pen-faint);
}

.form-error-text {
  color: var(--pen-danger);
  margin-top: var(--space-1);
  padding-left: 100px;
  font-size: var(--fs-sub);
}
</style>
