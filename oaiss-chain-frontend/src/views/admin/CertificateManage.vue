<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：管理员端 - 准入资质与专业执业证书管理 (Certificate Management)
 * 【路由地址】：/admin/certificates
 * 【访问权限】：ROLE_ADMIN (系统管理员)
 * 【界面可见功能】：
 *   1. 双 Tab 切换：企业准入证书 (Admission) / 核查员执业资格证 (Qualification)
 *   2. 证书签发对话框：选择候选企业/审核员，一键签发官方资质证书
 *   3. 证书吊销操作：针对违规主体执行吊销废止，阻断其市场操作权限
 *   4. 证书列表表格：展示持证主体、证书编号、签发时间、生效状态（有效/已吊销）
 *   5. 证书制度管理释义（PageHint / StatusTag）
 * 【对应后端 Controller & API】：
 *   - AdminController:
 *     * GET    /api/v1/admin/enterprise-admission              -> getEnterpriseAdmissionList()
 *     * POST   /api/v1/admin/enterprise-admission/{id}/issue   -> issueEnterpriseAdmission()
 *     * DELETE /api/v1/admin/enterprise-admission/{id}         -> revokeEnterpriseAdmission()
 *     * GET    /api/v1/admin/reviewer-qualification            -> getReviewerQualificationList()
 *     * POST   /api/v1/admin/reviewer-qualification/{id}/issue -> issueReviewerQualification()
 *     * DELETE /api/v1/admin/reviewer-qualification/{id}       -> revokeReviewerQualification()
 *   - CreditScoreController:
 *     * GET    /api/v1/credit/ranking                          -> getScoreRanking()
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Document, Stamp } from '@element-plus/icons-vue'
import {
  getEnterpriseAdmissionList,
  issueEnterpriseAdmission,
  revokeEnterpriseAdmission,
  getReviewerQualificationList,
  issueReviewerQualification,
  revokeReviewerQualification,
} from '../../api/admin'
import type { EnterpriseAdmissionResponse, ReviewerQualificationResponse } from '../../types'
import { formatDateTime } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import { getScoreRanking } from '../../api/credit'
import StatusTag from '../../components/StatusTag.vue'
import EmptyHint from '../../components/EmptyHint.vue'

const { t } = useI18n()

const activeTab = ref<'admission' | 'qualification'>('admission')

const admissionList = ref<EnterpriseAdmissionResponse[]>([])
const admissionLoading = ref(false)
const admissionPage = ref(1)
const admissionPageSize = ref(10)
const admissionTotal = ref(0)

const qualificationList = ref<ReviewerQualificationResponse[]>([])
const qualificationLoading = ref(false)
const qualificationPage = ref(1)
const qualificationPageSize = ref(10)
const qualificationTotal = ref(0)

const issueDialogVisible = ref(false)
const issueType = ref<'admission' | 'qualification'>('admission')
const issueForm = reactive({ enterpriseId: '', reviewerId: '' })

/** 签发企业下拉：信誉排名提供企业名单（enterpriseId 直接可用于签发） */
const enterpriseOptions = ref<Array<{ enterpriseId: number; label: string }>>([])
const enterpriseOptionsLoading = ref(false)

async function loadEnterpriseOptions(): Promise<void> {
  enterpriseOptionsLoading.value = true
  try {
    const page = await getScoreRanking({ pageNum: 1, pageSize: 50 })
    enterpriseOptions.value = (page?.items ?? [])
      .filter((item) => item.enterpriseId)
      .map((item) => ({ enterpriseId: item.enterpriseId, label: item.enterpriseName }))
  } catch {
    enterpriseOptions.value = []
  } finally {
    enterpriseOptionsLoading.value = false
  }
}

watch(issueType, () => {
  issueForm.enterpriseId = ''
  issueForm.reviewerId = ''
})

async function fetchAdmissionData(): Promise<void> {
  admissionLoading.value = true
  try {
    const res = await getEnterpriseAdmissionList({ pageNum: admissionPage.value, pageSize: admissionPageSize.value })
    admissionList.value = res.items
    admissionTotal.value = res.total
  } catch {
    ElMessage.error(t('certificateManage.loadFailed'))
  } finally {
    admissionLoading.value = false
  }
}

async function fetchQualificationData(): Promise<void> {
  qualificationLoading.value = true
  try {
    const res = await getReviewerQualificationList({ pageNum: qualificationPage.value, pageSize: qualificationPageSize.value })
    qualificationList.value = res.items
    qualificationTotal.value = res.total
  } catch {
    ElMessage.error(t('certificateManage.loadFailed'))
  } finally {
    qualificationLoading.value = false
  }
}

function openIssueDialog(type: 'admission' | 'qualification'): void {
  issueType.value = type
  issueDialogVisible.value = true
  void loadEnterpriseOptions()
}

function onTabChange(tab: 'admission' | 'qualification'): void {
  if (tab === 'admission') {
    void fetchAdmissionData()
  } else {
    void fetchQualificationData()
  }
}

async function handleIssueAdmission(): Promise<void> {
  const id = Number(issueForm.enterpriseId)
  if (!id || id <= 0) {
    ElMessage.warning(t('certificateManage.enterEnterpriseId'))
    return
  }

  try {
    await issueEnterpriseAdmission(id)
    ElMessage.success(t('certificateManage.issueSuccess'))
    issueDialogVisible.value = false
    await fetchAdmissionData()
  } catch {
    ElMessage.error(t('certificateManage.issueFailed'))
  }
}

async function handleIssueQualification(): Promise<void> {
  const id = Number(issueForm.reviewerId)
  if (!id || id <= 0) {
    ElMessage.warning(t('certificateManage.enterReviewerId'))
    return
  }

  try {
    await issueReviewerQualification(id)
    ElMessage.success(t('certificateManage.issueSuccess'))
    issueDialogVisible.value = false
    await fetchQualificationData()
  } catch {
    ElMessage.error(t('certificateManage.issueFailed'))
  }
}

/**
 * 吊销二次确认：把「为什么吊销」一并收集。
 *
 * 原先前端只弹一个「确定吗」，后端也只写 status，事后无法回答
 * 「这张证书因何被吊销」。原因可选（允许空），但必须给一次填写机会——
 * 吊销是不可逆操作，此刻不记，以后就永远记不上了。
 */
async function promptRevokeReason(): Promise<string | undefined> {
  const { value } = await ElMessageBox.prompt(
    t('certificateManage.confirmRevoke'),
    t('certificateManage.revokeTitle'),
    {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
      inputType: 'textarea',
      inputPlaceholder: t('certificateManage.revokeReasonPlaceholder'),
      inputValidator: (input: string) => !input || input.length <= 200
        ? true
        : t('certificateManage.revokeReasonTooLong'),
    },
  )
  const trimmed = (value || '').trim()
  return trimmed === '' ? undefined : trimmed
}

async function handleRevokeAdmission(row: EnterpriseAdmissionResponse): Promise<void> {
  try {
    const reason = await promptRevokeReason()
    await revokeEnterpriseAdmission(row.enterpriseId, reason)
    ElMessage.success(t('certificateManage.revokeSuccess'))
    await fetchAdmissionData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(t('certificateManage.revokeFailed'))
  }
}

async function handleRevokeQualification(row: ReviewerQualificationResponse): Promise<void> {
  try {
    const reason = await promptRevokeReason()
    await revokeReviewerQualification(row.reviewerId, reason)
    ElMessage.success(t('certificateManage.revokeSuccess'))
    await fetchQualificationData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(t('certificateManage.revokeFailed'))
  }
}

const getStatusType = (status: number) => (status === 1 ? 'success' : 'danger')
const getStatusText = (status: number) => (
  status === 1 ? t('certificateManage.active') : t('certificateManage.revoked')
)

/** 首屏统计条：全部由已加载的各 Tab 列表推导，不新增接口请求 */
const overviewStats = computed(() => {
  const admission = admissionList.value
  const activeAdmission = admission.filter((row) => Number(row.status) === 1).length
  const revokedAdmission = admission.filter((row) => Number(row.status) !== 1).length

  return [
    {
      label: t('certificateManage.statAdmission'),
      value: admissionTotal.value,
      unit: t('certificateManage.statUnitCerts'),
      delta: t('certificateManage.statAdmissionHint', { count: admission.length }),
      tone: 'neu' as const,
      icon: Document,
    },
    {
      label: t('certificateManage.statAdmissionActive'),
      value: activeAdmission,
      unit: t('certificateManage.statUnitCerts'),
      delta: t('certificateManage.statAdmissionActiveHint'),
      tone: 'pos' as const,
      icon: CircleCheck,
    },
    {
      label: t('certificateManage.statAdmissionRevoked'),
      value: revokedAdmission,
      unit: t('certificateManage.statUnitCerts'),
      delta: t('certificateManage.statAdmissionRevokedHint'),
      tone: revokedAdmission > 0 ? ('neg' as const) : ('neu' as const),
      icon: CircleClose,
    },
    {
      label: t('certificateManage.statQualification'),
      value: qualificationTotal.value,
      unit: t('certificateManage.statUnitCerts'),
      delta: t('certificateManage.statQualificationHint', { count: qualificationList.value.length }),
      tone: 'neu' as const,
      icon: Stamp,
    },
  ]
})

function onAdmissionSizeChange(size: number): void {
  admissionPageSize.value = size
  admissionPage.value = 1
  void fetchAdmissionData()
}

function onAdmissionCurrentChange(page: number): void {
  admissionPage.value = page
  void fetchAdmissionData()
}

function onQualificationSizeChange(size: number): void {
  qualificationPageSize.value = size
  qualificationPage.value = 1
  void fetchQualificationData()
}

function onQualificationCurrentChange(page: number): void {
  qualificationPage.value = page
  void fetchQualificationData()
}

onMounted(() => {
  void fetchAdmissionData()
})
</script>

<template>
  <section class="certificate-page pen-enter">
    <PageHeader
      :title="t('certificateManage.title')"
      :description="t('certificateManage.description')"
      :crumbs="[t('certificateManage.breadcrumbAdmin'), t('certificateManage.breadcrumbCertificate')]"
    />

    <PageHint :text="t('pageHints.certificateManage')" />

    <StatStrip :items="overviewStats" :loading="admissionLoading || qualificationLoading" />

    <el-tabs v-model="activeTab" class="certificate-tabs" @tab-change="onTabChange">
      <el-tab-pane :label="t('certificateManage.tabAdmission')" name="admission">
        <DataPanel
          :title="t('certificateManage.panelAdmissionTitle')"
          :meta="t('certificateManage.panelAdmissionMeta', { total: admissionTotal })"
        >
          <template #actions>
            <el-button type="primary" @click="openIssueDialog('admission')">
              {{ t('certificateManage.issue') }}
            </el-button>
          </template>

          <el-table :data="admissionList" v-loading="admissionLoading">
            <template #empty>
              <EmptyHint
                :description="t('nextSteps.emptyCertificatesTip')"
                :action-text="t('nextSteps.emptyCertificatesAction')"
                @action="openIssueDialog('admission')"
              />
            </template>
            <el-table-column prop="id" :label="t('certificateManage.colId')" width="80" />
            <el-table-column prop="enterpriseId" :label="t('certificateManage.colEnterpriseId')" min-width="120" />
            <el-table-column prop="certificateNo" :label="t('certificateManage.colCertificateNo')" min-width="180" />
            <el-table-column prop="issuedDate" :label="t('certificateManage.colIssuedDate')" min-width="120" />
            <el-table-column prop="status" :label="t('certificateManage.colStatus')" min-width="100">
              <template #default="{ row }">
                <StatusTag :type="getStatusType(row.status)" :text="getStatusText(row.status)" />
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" :label="t('certificateManage.colCreateTime')" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('certificateManage.colOperation')" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 1" link type="danger" @click="handleRevokeAdmission(row)">
                  {{ t('certificateManage.revoke') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <template #footer>
            <el-pagination
              v-model:current-page="admissionPage"
              v-model:page-size="admissionPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="admissionTotal"
              @size-change="onAdmissionSizeChange"
              @current-change="onAdmissionCurrentChange"
            />
          </template>
        </DataPanel>
      </el-tab-pane>

      <el-tab-pane :label="t('certificateManage.tabQualification')" name="qualification">
        <DataPanel
          :title="t('certificateManage.panelQualificationTitle')"
          :meta="t('certificateManage.panelQualificationMeta', { total: qualificationTotal })"
        >
          <template #actions>
            <el-button type="primary" @click="issueDialogVisible = true; issueType = 'qualification'">
              {{ t('certificateManage.issue') }}
            </el-button>
          </template>

          <el-table :data="qualificationList" v-loading="qualificationLoading">
            <template #empty>
              <EmptyHint
                :description="t('nextSteps.emptyCertificatesTip')"
                :action-text="t('nextSteps.emptyCertificatesAction')"
                @action="issueDialogVisible = true; issueType = 'qualification'"
              />
            </template>
            <el-table-column prop="id" :label="t('certificateManage.colId')" width="80" />
            <el-table-column prop="reviewerId" :label="t('certificateManage.colReviewerId')" min-width="120" />
            <el-table-column prop="certificateNo" :label="t('certificateManage.colCertificateNo')" min-width="180" />
            <el-table-column prop="qualificationType" :label="t('certificateManage.colQualificationType')" min-width="150" />
            <el-table-column prop="issuedDate" :label="t('certificateManage.colIssuedDate')" min-width="120" />
            <el-table-column prop="status" :label="t('certificateManage.colStatus')" min-width="100">
              <template #default="{ row }">
                <StatusTag :type="getStatusType(row.status)" :text="getStatusText(row.status)" />
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" :label="t('certificateManage.colCreateTime')" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('certificateManage.colOperation')" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 1" link type="danger" @click="handleRevokeQualification(row)">
                  {{ t('certificateManage.revoke') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <template #footer>
            <el-pagination
              v-model:current-page="qualificationPage"
              v-model:page-size="qualificationPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="qualificationTotal"
              @size-change="onQualificationSizeChange"
              @current-change="onQualificationCurrentChange"
            />
          </template>
        </DataPanel>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="issueDialogVisible" :title="t('certificateManage.issue')" width="400px">
      <el-form v-if="issueType === 'admission'">
        <el-form-item :label="t('certificateManage.colEnterpriseId')">
          <el-select
            v-model="issueForm.enterpriseId"
            filterable
            :loading="enterpriseOptionsLoading"
            :placeholder="t('certificateManage.enterpriseSelectPlaceholder')"
            style="width: 100%"
          >
            <el-option
              v-for="option in enterpriseOptions"
              :key="option.enterpriseId"
              :label="option.label"
              :value="option.enterpriseId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-form v-else>
        <el-form-item :label="t('certificateManage.colReviewerId')">
          <el-input v-model="issueForm.reviewerId" :placeholder="t('certificateManage.enterReviewerId')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="issueType === 'admission' ? handleIssueAdmission() : handleIssueQualification()">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.certificate-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.certificate-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--space-4);
}
</style>
