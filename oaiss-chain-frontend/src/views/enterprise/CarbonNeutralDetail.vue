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
 *   5. 生命周期推进操作：提交立项、启动实施、申请认证与项目终止
 * 【对应后端 Controller & API】：
 *   - CarbonNeutralProjectController:
 *     * GET  /api/v1/carbon-neutral/{id}                     -> getProject() 获取项目详情
 *     * POST /api/v1/carbon-neutral/{id}/submit              -> submitProject() 提交立项
 *     * POST /api/v1/carbon-neutral/{id}/start               -> startProject() 启动实施
 *     * PUT  /api/v1/carbon-neutral/{id}/monitoring          -> updateMonitoring() 追加监测数据
 *     * POST /api/v1/carbon-neutral/{id}/apply-certification -> applyCertification() 申请认证
 *     * POST /api/v1/carbon-neutral/{id}/terminate           -> terminateProject() 终止项目
 * ==============================================================================
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getProject,
  submitProject,
  startProject,
  updateMonitoring,
  applyCertification,
  terminateProject,
} from '../../api/carbonNeutral'
import type { CarbonNeutralProjectResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageContainer from '../../components/PageContainer.vue'
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

const route = useRoute()
const projectId = computed(() => Number(route.params.id))

const loading = ref(false)
const project = ref<CarbonNeutralProjectResponse | null>(null)

const activeTab = ref('info')
const monitorForm = ref({ emissionData: '', description: '' })
const monitorLoading = ref(false)

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

const onApplyCertification = () => handleAction(
  () => applyCertification(projectId.value),
  t('carbonNeutralDetail.confirmApplyCertification'),
)

const onTerminate = () => handleAction(
  () => terminateProject(projectId.value, { reason: t('carbonNeutral.terminateReason') }),
  t('carbonNeutralDetail.confirmTerminate'),
)

const onSaveMonitoring = async () => {
  try {
    monitorLoading.value = true
    await updateMonitoring(projectId.value, { monitoringData: monitorForm.value })
    ElMessage.success(t('carbonNeutralDetail.monitorUpdateSuccess'))
    monitorForm.value = { emissionData: '', description: '' }
    await loadProject()
  } catch {
    ElMessage.error(t('carbonNeutralDetail.monitorUpdateFailed'))
  } finally {
    monitorLoading.value = false
  }
}

const canSubmit = computed(() => currentStatus.value === PROJECT_STATUS.DRAFT)
const canStart = computed(() => currentStatus.value === PROJECT_STATUS.APPROVED)
const canCertify = computed(() => currentStatus.value === PROJECT_STATUS.IMPLEMENTING)
const canTerminate = computed(() => {
  const status = currentStatus.value
  return status === PROJECT_STATUS.APPROVED || status === PROJECT_STATUS.IMPLEMENTING
})
const canEditMonitoring = computed(() => currentStatus.value === PROJECT_STATUS.IMPLEMENTING)

onMounted(() => {
  loadProject()
})
</script>

<template>
  <PageContainer :title="t('carbonNeutralDetail.title')" :description="t('carbonNeutralDetail.description')">
    <section class="detail-page" v-loading="loading">
      <el-card v-if="project" class="section-card" shadow="never">
        <template #header>
          <div class="card-header-row">
            <span class="card-header">{{ project.projectName }}</span>
            <div class="action-bar">
              <StatusTag domain="project" :status="currentStatus" :text="statusText" size="large" />
              <el-button v-if="canSubmit" type="primary" @click="onSubmit">{{ t('carbonNeutralDetail.actionSubmitReview') }}</el-button>
              <el-button v-if="canStart" type="success" @click="onStart">{{ t('carbonNeutralDetail.actionStartImplement') }}</el-button>
              <el-button v-if="canCertify" type="success" @click="onApplyCertification">{{ t('carbonNeutralDetail.actionApplyCertification') }}</el-button>
              <el-button v-if="canTerminate" type="danger" @click="onTerminate">{{ t('carbonNeutralDetail.actionTerminate') }}</el-button>
            </div>
          </div>
        </template>

        <el-tabs v-model="activeTab">
          <el-tab-pane :label="t('carbonNeutralDetail.tabInfo')" name="info">
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
          </el-tab-pane>

          <el-tab-pane :label="t('carbonNeutralDetail.tabMonitor')" name="monitoring" :disabled="!canEditMonitoring">
            <div style="padding: 20px 0; max-width: 700px">
              <el-form label-width="120px">
                <el-form-item :label="t('carbonNeutralDetail.monitorEmissionData')">
                  <el-input
                    v-model="monitorForm.emissionData"
                    type="textarea"
                    :rows="6"
                    :placeholder="t('carbonNeutralDetail.monitorEmissionPlaceholder')"
                  />
                </el-form-item>
                <el-form-item :label="t('carbonNeutralDetail.monitorDescription')">
                  <el-input
                    v-model="monitorForm.description"
                    type="textarea"
                    :rows="3"
                    :placeholder="t('carbonNeutralDetail.monitorDescriptionPlaceholder')"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="monitorLoading" @click="onSaveMonitoring">{{ t('carbonNeutralDetail.monitorUpdate') }}</el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <el-empty v-else-if="!loading" :description="t('carbonNeutralDetail.notFound')" />
    </section>
  </PageContainer>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.card-header {
  font-weight: 600;
  font-size: var(--text-lg);
}

.action-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
