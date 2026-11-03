<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳中和项目全景详情与监测看板 (Carbon Neutral Project Detail)
 * 【路由地址】：/enterprise/carbon-neutral/projects/:id
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 项目全景档案面板：展示项目编码、核算方法学、立项单位、执行周期与当前状态
 *   2. 监测期数据填报与历史记录：实施阶段追加实测监测数据（实测发电、减排凭证）
 *   3. 减排核证与碳信用签发跟踪：查看第三方专家核定减排量与正式签发证书编号
 *   4. 碳信用抵消注销操作：录入额度执行企业自身超标排放的碳信用冲抵核销
 * 【对应后端 Controller & API】：
 *   - CarbonNeutralProjectController:
 *     * GET  /api/v1/carbon-neutral/{id}                     -> getProject() 获取项目详情
 *     * POST /api/v1/carbon-neutral/{id}/submit              -> submitProject() 提交立项
 *     * POST /api/v1/carbon-neutral/{id}/start               -> startProject() 启动实施
 *     * POST /api/v1/carbon-neutral/{id}/submit-verification -> submitVerification() 申请核证
 *     * PUT  /api/v1/carbon-neutral/{id}/monitoring          -> updateMonitoring() 追加监测数据
 *     * POST /api/v1/carbon-neutral/{id}/apply-certification -> applyCertification() 申请认证
 *     * POST /api/v1/carbon-neutral/{id}/terminate           -> terminateProject() 终止项目
 * ==============================================================================
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Coin, Odometer, TrendCharts, CircleCheck } from '@element-plus/icons-vue'
import {
  getProject,
  submitProject,
  startProject,
  submitVerification,
  updateMonitoring,
  applyCertification,
  terminateProject,
} from '../../api/carbonNeutral'
import type { CarbonNeutralProjectResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import StatusTag from '../../components/StatusTag.vue'

const { t } = useI18n()

const PROJECT_STATUS = {
  DRAFT: 0,
  PENDING: 1,
  APPROVED: 2,
  IMPLEMENTING: 3,
  COMPLETED: 4,
  TERMINATED: 5,
  REJECTED: 6,
} as const

/** 与后端 CarbonNeutralProjectService.VERIFY_STATUS_* 对齐 */
const VERIFY_STATUS = {
  NONE: 0,
  PENDING: 1,
  VERIFIED: 2,
  FAILED: 3,
} as const

/** 与后端 CarbonNeutralProjectService.CERT_STATUS_* 对齐 */
const CERT_STATUS = {
  NONE: 0,
  PENDING: 1,
  CERTIFIED: 2,
  FAILED: 3,
} as const

const route = useRoute()
const projectId = computed(() => Number(route.params.id))

const loading = ref(false)
const project = ref<CarbonNeutralProjectResponse | null>(null)

const activeTab = ref('info')

const loadProject = async () => {
  try {
    loading.value = true
    const result = await getProject(projectId.value)
    project.value = result
  } catch {
    ElMessage.error(t('carbonNeutralDetail.loadFailed'))
  } finally {
    loading.value = false
  }
}

const currentStatus = computed(() => {
  const status = project.value?.status
  return typeof status === 'number' ? status : null
})

const currentVerificationStatus = computed(() => {
  const status = project.value?.verificationStatus
  return typeof status === 'number' ? status : null
})

const currentCertStatus = computed(() => {
  const status = project.value?.certStatus
  return typeof status === 'number' ? status : null
})

const fallbackStatusText = (status: number | null) => {
  const map: Record<number, string> = {
    [PROJECT_STATUS.DRAFT]: t('carbonNeutralDetail.statusDraft'),
    [PROJECT_STATUS.PENDING]: t('carbonNeutralDetail.statusPending'),
    [PROJECT_STATUS.APPROVED]: t('carbonNeutralDetail.statusApproved'),
    [PROJECT_STATUS.IMPLEMENTING]: t('carbonNeutralDetail.statusImplementing'),
    [PROJECT_STATUS.COMPLETED]: t('carbonNeutralDetail.statusCertified'),
    [PROJECT_STATUS.TERMINATED]: t('carbonNeutralDetail.statusTerminated'),
    [PROJECT_STATUS.REJECTED]: t('carbonNeutralDetail.statusRejected'),
  }
  return status !== null ? (map[status] || String(status)) : '-'
}

const statusText = computed(() => project.value?.statusText || fallbackStatusText(currentStatus.value))

const handleAction = async (action: () => Promise<void>, confirmMsg: string) => {
  try {
    await ElMessageBox.confirm(confirmMsg, t('carbonNeutralDetail.confirmAction'), { type: 'warning' })
    loading.value = true
    await action()
    ElMessage.success(t('carbonNeutralDetail.actionSuccess'))
    await loadProject()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('carbonNeutralDetail.actionFailed'))
    }
  } finally {
    loading.value = false
  }
}

const onSubmit = () => handleAction(
  () => submitProject(projectId.value),
  t('carbonNeutralDetail.confirmSubmitReview'),
)

const onStart = () => handleAction(
  () => startProject(projectId.value),
  t('carbonNeutralDetail.confirmStartImplement'),
)

/**
 * 申请核证：后端 SubmitVerificationRequest 必填 verifierId（审核员 userId），
 * 项目ID 走路径参数。当前没有面向企业端的审核员名录接口，故由企业填写核证员 ID。
 */
const onSubmitVerification = async () => {
  let verifierId: number
  try {
    const { value } = await ElMessageBox.prompt(
      t('carbonNeutralDetail.enterVerifierId'),
      t('carbonNeutralDetail.dialogSubmitVerifyTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputValue: project.value?.verifierId ? String(project.value.verifierId) : '',
        inputPattern: /^[1-9]\d*$/,
        inputErrorMessage: t('carbonNeutralDetail.verifierIdInvalid'),
      },
    )
    verifierId = Number(value)
  } catch {
    return
  }
  await handleAction(
    () => submitVerification(projectId.value, { verifierId }),
    t('carbonNeutralDetail.confirmSubmitVerification'),
  )
}

/** 追加监测数据：后端 MonitoringUpdateRequest.monitoringData 为 @NotBlank 字符串，空值不发请求 */
const onUpdateMonitoring = async () => {
  let monitoringData: string
  try {
    const { value } = await ElMessageBox.prompt(
      t('carbonNeutralDetail.monitorEmissionPlaceholder'),
      t('carbonNeutralDetail.panelMonitorTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputType: 'textarea',
        inputPattern: /\S+/,
        inputErrorMessage: t('carbonNeutralDetail.monitoringRequired'),
      },
    )
    monitoringData = value.trim()
  } catch {
    return
  }
  if (!monitoringData) {
    ElMessage.warning(t('carbonNeutralDetail.monitoringRequired'))
    return
  }
  await handleAction(
    () => updateMonitoring(projectId.value, { monitoringData }),
    t('carbonNeutralDetail.confirmUpdateMonitoring'),
  )
}

/** 申请认证：后端 ApplyCertificationRequest.certOrg 为 @NotBlank，必须随请求提交 */
const onApplyCertification = async () => {
  let certOrg: string
  try {
    const { value } = await ElMessageBox.prompt(
      t('carbonNeutralDetail.enterCertOrg'),
      t('carbonNeutralDetail.dialogApplyCertificationTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputValue: project.value?.certOrg || '',
        inputPattern: /\S+/,
        inputErrorMessage: t('carbonNeutralDetail.certOrgRequired'),
      },
    )
    certOrg = value.trim()
  } catch {
    return
  }
  if (!certOrg) {
    ElMessage.warning(t('carbonNeutralDetail.certOrgRequired'))
    return
  }
  await handleAction(
    () => applyCertification(projectId.value, { certOrg }),
    t('carbonNeutralDetail.confirmApplyCertification'),
  )
}

/** 终止项目：后端 TerminateProjectRequest.reason 为 @NotBlank，必须随请求提交 */
const onTerminate = async () => {
  let reason: string
  try {
    const { value } = await ElMessageBox.prompt(
      t('carbonNeutralDetail.confirmTerminate'),
      t('carbonNeutralDetail.actionTerminate'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputType: 'textarea',
        inputValue: t('carbonNeutral.terminateReason'),
        inputPattern: /\S+/,
        inputErrorMessage: t('carbonNeutralDetail.terminateReasonRequired'),
      },
    )
    reason = value.trim()
  } catch {
    return
  }
  if (!reason) {
    ElMessage.warning(t('carbonNeutralDetail.terminateReasonRequired'))
    return
  }
  await handleAction(
    () => terminateProject(projectId.value, { reason }),
    t('carbonNeutralDetail.confirmTerminate'),
  )
}

const canSubmit = computed(() => currentStatus.value === PROJECT_STATUS.DRAFT)
const canStart = computed(() => currentStatus.value === PROJECT_STATUS.APPROVED)
/** 申请核证：后端接受实施中/已完成且未核证的项目，本阶段按钮按计划只在实施中展示 */
const canSubmitVerification = computed(() => currentStatus.value === PROJECT_STATUS.IMPLEMENTING)
const canUpdateMonitoring = computed(() => currentStatus.value === PROJECT_STATUS.IMPLEMENTING)
const canApplyCertification = computed(() => currentVerificationStatus.value === VERIFY_STATUS.VERIFIED
  && currentCertStatus.value === CERT_STATUS.NONE)
const canTerminate = computed(() => currentStatus.value === PROJECT_STATUS.APPROVED
  || currentStatus.value === PROJECT_STATUS.IMPLEMENTING)

/**
 * 页头生命周期动作：按钮集合完全由当前状态推导，与原 v-if 条件一一对应；
 * 事件索引 → 处理函数也按同一份列表映射，行为与迁移前一致。
 */
// W6 裁剪已于 Phase 7 撤销（依据见 07-CONTEXT D-02/D-12）：申请核证、追加监测数据、
// 申请认证、终止四个动作及其后端端点全部恢复。
type LifecycleKey = 'submit' | 'start' | 'submitVerification' | 'updateMonitoring' | 'applyCertification' | 'terminate'

const lifecycleActions = computed<Array<{ key: LifecycleKey; label: string; variant: 'primary' | 'default' }>>(() => {
  const actions: Array<{ key: LifecycleKey; label: string; variant: 'primary' | 'default' }> = []
  if (canSubmit.value) actions.push({ key: 'submit', label: t('carbonNeutralDetail.actionSubmitReview'), variant: 'primary' })
  if (canStart.value) actions.push({ key: 'start', label: t('carbonNeutralDetail.actionStartImplement'), variant: 'primary' })
  if (canSubmitVerification.value) actions.push({ key: 'submitVerification', label: t('carbonNeutralDetail.actionSubmitVerification'), variant: 'primary' })
  if (canUpdateMonitoring.value) actions.push({ key: 'updateMonitoring', label: t('carbonNeutralDetail.actionUpdateMonitoring'), variant: 'default' })
  if (canApplyCertification.value) actions.push({ key: 'applyCertification', label: t('carbonNeutralDetail.actionApplyCertification'), variant: 'primary' })
  if (canTerminate.value) actions.push({ key: 'terminate', label: t('carbonNeutralDetail.actionTerminate'), variant: 'default' })
  return actions
})

const lifecycleHandlers: Record<LifecycleKey, () => Promise<void> | void> = {
  submit: onSubmit,
  start: onStart,
  submitVerification: onSubmitVerification,
  updateMonitoring: onUpdateMonitoring,
  applyCertification: onApplyCertification,
  terminate: onTerminate,
}

function onHeaderAction(index: number) {
  const action = lifecycleActions.value[index]
  if (action) lifecycleHandlers[action.key]?.()
}

/** 首屏统计条：全部取自已加载的项目详情，不新增接口请求 */
const projectStats = computed(() => {
  const p = project.value
  const expected = Number(p?.expectedReduction) || 0
  const actual = Number(p?.actualReduction) || 0
  const progress = expected > 0 ? Math.min(100, Math.round((actual / expected) * 100)) : 0
  const availableCredits = Number(p?.availableCredits) || 0

  return [
    {
      label: t('carbonNeutralDetail.statExpectedReduction'),
      value: Number(expected.toFixed(1)),
      unit: 'tCO2e',
      delta: t('carbonNeutralDetail.statExpectedHint'),
      tone: 'neu' as const,
      icon: TrendCharts,
    },
    {
      label: t('carbonNeutralDetail.statActualReduction'),
      value: Number(actual.toFixed(1)),
      unit: 'tCO2e',
      delta: t('carbonNeutralDetail.statProgressHint', { percent: progress }),
      tone: actual > 0 ? ('pos' as const) : ('neu' as const),
      icon: Odometer,
    },
    {
      label: t('carbonNeutralDetail.statAvailableCredits'),
      value: Number(availableCredits.toFixed(1)),
      unit: t('carbonNeutralDetail.statUnitCredit'),
      delta: t('carbonNeutralDetail.statIssuedHint', { issued: Number(Number(p?.issuedCredits || 0).toFixed(1)) }),
      tone: availableCredits > 0 ? ('pos' as const) : ('neu' as const),
      icon: Coin,
    },
    {
      label: t('carbonNeutralDetail.statCurrentStatus'),
      value: statusText.value,
      delta: t('carbonNeutralDetail.statStatusHint'),
      tone: currentStatus.value === PROJECT_STATUS.COMPLETED
        ? ('pos' as const)
        : currentStatus.value === PROJECT_STATUS.TERMINATED || currentStatus.value === PROJECT_STATUS.REJECTED
          ? ('neg' as const)
          : ('neu' as const),
      icon: CircleCheck,
    },
  ]
})

onMounted(() => {
  loadProject()
})
</script>

<template>
  <section class="detail-page pen-enter" v-loading="loading">
    <PageHeader
      :title="project?.projectName || t('carbonNeutralDetail.title')"
      :description="t('carbonNeutralDetail.description')"
      :crumbs="[t('carbonNeutralDetail.breadcrumbCarbon'), t('carbonNeutralDetail.breadcrumbDetail')]"
      :actions="lifecycleActions"
      @action="onHeaderAction"
    />

    <StatStrip :items="projectStats" :loading="loading" />

    <template v-if="project">
      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane :label="t('carbonNeutralDetail.tabInfo')" name="info">
          <DataPanel
            :title="t('carbonNeutralDetail.panelTitle')"
            :meta="t('carbonNeutralDetail.panelMeta', { no: project.projectNo })"
          >
            <template #actions>
              <StatusTag domain="project" :status="currentStatus" :text="statusText" size="large" />
            </template>

            <div class="detail-body">
              <el-descriptions :column="2" border>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelProjectName')">{{ project.projectName }}</el-descriptions-item>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelProjectType')">{{ project.projectTypeName || project.projectType }}</el-descriptions-item>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelExpectedReduction')">{{ formatNumber(project.expectedReduction) }} {{ t('common.unit_ton') }}</el-descriptions-item>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelActualReduction')">{{ project.actualReduction != null ? formatNumber(project.actualReduction) : '-' }} {{ t('common.unit_ton') }}</el-descriptions-item>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelStartDate')">{{ project.startDate }}</el-descriptions-item>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelEndDate')">{{ project.endDate }}</el-descriptions-item>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelCreateTime')">{{ formatDateTime(project.createdAt) }}</el-descriptions-item>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelUpdateTime')">{{ formatDateTime(project.updatedAt) || '-' }}</el-descriptions-item>
                <el-descriptions-item :label="t('common.status')">{{ statusText }}</el-descriptions-item>
                <el-descriptions-item :label="t('carbonNeutralDetail.labelDescription')" :span="2">{{ project.description }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </DataPanel>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-empty v-else-if="!loading" :description="t('carbonNeutralDetail.notFound')" />
  </section>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--space-4);
}

.detail-body {
  padding: var(--space-5) var(--space-4);
}

.monitor-body {
  max-width: 700px;
  padding: var(--space-5) var(--space-4);
}
</style>
