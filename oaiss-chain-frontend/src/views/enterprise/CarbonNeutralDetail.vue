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


const canSubmit = computed(() => currentStatus.value === PROJECT_STATUS.DRAFT)
const canStart = computed(() => currentStatus.value === PROJECT_STATUS.APPROVED)

/**
 * 页头生命周期动作：按钮集合完全由当前状态推导，与原 v-if 条件一一对应；
 * 事件索引 → 处理函数也按同一份列表映射，行为与迁移前一致。
 */
// W6 收口：生命周期只保留前四步（创建 → 提交 → 审核 → 实施）。
// 申请核证 / 认证 / 终止三个动作已移除，对应后端端点同步删除。
// 理由：后三步不产生新的真实数据（核证=第三方再确认一次，认证=审核员再通过一次），
// 且该业务全程不上链，不产生任何可信证据——保留只会让状态机看起来完整而实际空转。
type LifecycleKey = 'submit' | 'start'

const lifecycleActions = computed<Array<{ key: LifecycleKey; label: string; variant: 'primary' | 'default' }>>(() => {
  const actions: Array<{ key: LifecycleKey; label: string; variant: 'primary' | 'default' }> = []
  if (canSubmit.value) actions.push({ key: 'submit', label: t('carbonNeutralDetail.actionSubmitReview'), variant: 'primary' })
  if (canStart.value) actions.push({ key: 'start', label: t('carbonNeutralDetail.actionStartImplement'), variant: 'primary' })
  return actions
})

const lifecycleHandlers: Record<LifecycleKey, () => Promise<void> | void> = {
  submit: onSubmit,
  start: onStart,
}

function onHeaderAction(index: number) {
  const action = lifecycleActions.value[index]
  if (action) lifecycleHandlers[action.key]()
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
