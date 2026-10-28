<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：第三方监管端 - 全局宏观碳监管驾驶舱 (Regulatory Monitor)
 * 【路由地址】：/third-party/monitor
 * 【访问权限】：ROLE_THIRD_PARTY (第三方监管机构)
 * 【界面可见功能】：
 *   1. 辖区宏观监管统计卡片：管辖企业报告总数、待初审数、审核通过率与违规驳回率
 *   2. 监管机构主体档案卡片：展示机构全称、统一社会信用代码与法定资质
 *   3. 机构联络人维护表单：更新监管专员姓名与应急联络电话
 *   4. 全辖区排碳报告穿透审计列表：多条件检索辖区内企业碳报告（按企业ID、状态等）
 *   5. 监管职责与合规标准释义（PageHint / StatusTag）
 * 【对应后端 Controller & API】：
 *   - ThirdPartyController:
 *     * GET /api/v1/third-party/statistics     -> getStatistics() 监管宏观统计指标
 *     * GET /api/v1/third-party/org-info       -> getOrgInfo() 获取监管机构档案
 *     * PUT /api/v1/third-party/contact        -> updateContact() 更新监管机构联系人
 *     * GET /api/v1/third-party/carbon-reports -> getCarbonReports() 穿透查询企业碳报告
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getCarbonReports, getOrgInfo, getStatistics, updateContact } from '../../api/thirdParty'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageContainer from '../../components/PageContainer.vue'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'
import type { CarbonReport, ThirdPartyOrg, ThirdPartyStatistics } from '../../types'

const { t } = useI18n()

const statistics = ref<ThirdPartyStatistics>({
  totalReports: 0,
  pendingReports: 0,
  approvedReports: 0,
  rejectedReports: 0,
})

const orgInfo = ref<ThirdPartyOrg | null>(null)
const orgLoading = ref(false)
const savingContact = ref(false)

const searchForm = reactive<{
  enterpriseId: string
  keyword: string
  status: '' | number
}>({
  enterpriseId: '',
  keyword: '',
  status: '',
})

const contactForm = reactive({
  contactPerson: '',
  contactPhone: '',
})

const approvalRate = computed(() => {
  const totalReviewed = statistics.value.approvedReports + statistics.value.rejectedReports
  if (totalReviewed === 0) return '0%'
  return `${((statistics.value.approvedReports / totalReviewed) * 100).toFixed(1)}%`
})

const numericApprovalRate = computed(() => {
  const totalReviewed = statistics.value.approvedReports + statistics.value.rejectedReports
  if (totalReviewed === 0) return 0
  return Number(((statistics.value.approvedReports / totalReviewed) * 100).toFixed(1))
})

const numericPendingRate = computed(() => {
  if (statistics.value.totalReports === 0) return 0
  return Number(((statistics.value.pendingReports / statistics.value.totalReports) * 100).toFixed(1))
})

const statsLoading = ref(false)

const reports = ref<CarbonReport[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const loadStatistics = async () => {
  try {
    statsLoading.value = true
    const result = await getStatistics()
    statistics.value = {
      orgName: result?.orgName,
      accessLevel: result?.accessLevel,
      totalReports: result?.totalReports || 0,
      pendingReports: result?.pendingReports || 0,
      approvedReports: result?.approvedReports || 0,
      rejectedReports: result?.rejectedReports || 0,
    }
  } catch (error) {
    console.error('Failed to load statistics:', error)
    ElMessage.error(t('monitor.loadFailed'))
  } finally {
    statsLoading.value = false
  }
}

const loadOrgInfo = async () => {
  try {
    orgLoading.value = true
    const result = await getOrgInfo()
    orgInfo.value = result
    contactForm.contactPerson = result?.contactPerson || ''
    contactForm.contactPhone = result?.contactPhone || ''
  } catch {
    orgInfo.value = null
    ElMessage.error(t('monitor.loadOrgFailed'))
  } finally {
    orgLoading.value = false
  }
}

const loadReports = async () => {
  try {
    loading.value = true
    const enterpriseIdValue = searchForm.enterpriseId.trim()
    const keyword = searchForm.keyword.trim()
    const enterpriseId = enterpriseIdValue ? Number(enterpriseIdValue) : undefined
    const result = await getCarbonReports({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      enterpriseId: Number.isFinite(enterpriseId) ? enterpriseId : undefined,
      keyword: keyword || undefined,
      status: searchForm.status || undefined,
    })
    reports.value = result?.items || []
    total.value = result?.total || 0
  } catch {
    ElMessage.error(t('monitor.loadFailed'))
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  currentPage.value = 1
  loadReports()
}

const onReset = () => {
  searchForm.enterpriseId = ''
  searchForm.keyword = ''
  searchForm.status = ''
  currentPage.value = 1
  loadReports()
}

const saveContact = async () => {
  const contactPerson = contactForm.contactPerson.trim()
  const contactPhone = contactForm.contactPhone.trim()

  if (!contactPerson || !contactPhone) {
    ElMessage.warning(t('monitor.contactSaveFailed'))
    return
  }

  if (!/^\d{7,20}$/.test(contactPhone)) {
    ElMessage.warning(t('monitor.contactSaveFailed'))
    return
  }

  try {
    savingContact.value = true
    await updateContact({
      contactPerson,
      contactPhone,
    })
    ElMessage.success(t('monitor.contactSaveSuccess'))
    await loadOrgInfo()
  } catch (error: unknown) {
    const maybeError = error as {
      response?: { data?: { data?: string[]; message?: string } }
      message?: string
    }
    const backendMessage =
      maybeError?.response?.data?.data?.[0] ||
      maybeError?.response?.data?.message ||
      maybeError?.message
    ElMessage.error(backendMessage || t('monitor.contactSaveFailed'))
  } finally {
    savingContact.value = false
  }
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadReports()
}

const onCurrentChange = (page: number) => {
  currentPage.value = page
  loadReports()
}

onMounted(() => {
  loadStatistics()
  loadOrgInfo()
  loadReports()
})
</script>

<template>
  <PageContainer :title="t('monitor.title')" :description="t('monitor.description')">
    <PageHint :text="t('pageHints.monitor')" />
    <section class="monitor-page">
      <el-card class="section-card" shadow="never" v-loading="orgLoading">
        <template #header>
          <span class="card-header">{{ t('monitor.orgInfoTitle') }}</span>
        </template>

        <div v-if="orgInfo" class="org-grid">
          <div><strong>{{ t('monitor.orgName') }}:</strong> {{ orgInfo.orgName || '-' }}</div>
          <div><strong>{{ t('monitor.accessLevel') }}:</strong> {{ orgInfo.accessLevel || '-' }}</div>
          <div><strong>{{ t('monitor.address') }}:</strong> {{ orgInfo.address || '-' }}</div>
          <div><strong>{{ t('monitor.contactPerson') }}:</strong> {{ orgInfo.contactPerson || '-' }}</div>
          <div><strong>{{ t('monitor.contactPhone') }}:</strong> {{ orgInfo.contactPhone || '-' }}</div>
        </div>

        <el-form :inline="true" class="contact-form">
          <el-form-item :label="t('monitor.contactPerson')">
            <el-input v-model="contactForm.contactPerson" />
          </el-form-item>
          <el-form-item :label="t('monitor.contactPhone')">
            <el-input v-model="contactForm.contactPhone" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingContact" @click="saveContact">
              {{ t('common.save') }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="section-card" shadow="never" v-loading="statsLoading">
        <template #header>
          <span class="card-header">{{ t('monitor.dataStats') }}</span>
        </template>
        <div class="stats-grid">
          <div class="stat-card total">
            <div class="stat-content">
              <div class="stat-label">{{ t('monitor.statTotalReports') }}</div>
              <div class="stat-value">{{ statistics.totalReports }}</div>
            </div>
          </div>
          <div class="stat-card pending">
            <div class="stat-content">
              <div class="stat-label">{{ t('monitor.statPending') }}</div>
              <div class="stat-value">{{ statistics.pendingReports }}</div>
            </div>
          </div>
          <div class="stat-card approved">
            <div class="stat-content">
              <div class="stat-label">{{ t('monitor.statApproved') }}</div>
              <div class="stat-value">{{ statistics.approvedReports }}</div>
            </div>
          </div>
          <div class="stat-card rejected">
            <div class="stat-content">
              <div class="stat-label">{{ t('monitor.statRejected') }}</div>
              <div class="stat-value">{{ statistics.rejectedReports }}</div>
            </div>
          </div>
          <div class="stat-card rate">
            <div class="stat-content">
              <div class="stat-label">{{ t('monitor.statApprovalRate') }}</div>
              <div class="stat-value">{{ approvalRate }}</div>
            </div>
          </div>
        </div>

        <div class="stats-dials-row">
          <div class="dial-card">
            <el-progress type="dashboard" :percentage="numericApprovalRate" :color="'#14a79a'" :width="110">
              <template #default="{ percentage }">
                <div class="dial-inner">
                  <span class="dial-val">{{ percentage }}%</span>
                  <span class="dial-lbl">{{ t('monitor.statApprovalRate') }}</span>
                </div>
              </template>
            </el-progress>
          </div>
          <div class="dial-card">
            <el-progress type="dashboard" :percentage="numericPendingRate" :color="'#e6a23c'" :width="110">
              <template #default="{ percentage }">
                <div class="dial-inner">
                  <span class="dial-val">{{ percentage }}%</span>
                  <span class="dial-lbl">{{ t('monitor.statPending') }}</span>
                </div>
              </template>
            </el-progress>
          </div>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>
          <span class="card-header">{{ t('monitor.filterTitle') }}</span>
        </template>
        <el-form :inline="true" class="search-form">
          <el-form-item :label="t('monitor.enterpriseIdLabel')">
            <el-input v-model="searchForm.enterpriseId" :placeholder="t('monitor.enterpriseIdPlaceholder')" clearable />
          </el-form-item>
          <el-form-item :label="t('common.enterKeyword')">
            <el-input v-model="searchForm.keyword" :placeholder="t('common.enterKeyword')" clearable />
          </el-form-item>
          <el-form-item :label="t('monitor.colStatus')">
            <el-select v-model="searchForm.status" clearable style="width: 160px">
              <el-option :label="t('monitor.statusDraft')" :value="0" />
              <el-option :label="t('monitor.statusPending')" :value="1" />
              <el-option :label="t('monitor.statusApproved')" :value="3" />
              <el-option :label="t('monitor.statusRejected')" :value="4" />
              <el-option :label="t('monitor.statusOnChain')" :value="5" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">{{ t('common.search') }}</el-button>
            <el-button @click="onReset">{{ t('common.reset') }}</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>
          <span class="card-header">{{ t('monitor.tableTitle') }}</span>
        </template>
        <el-table :data="reports" border v-loading="loading">
          <el-table-column :label="t('common.colIndex')" width="80">
            <template #default="scope">
              {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
            </template>
          </el-table-column>
          <el-table-column prop="reportNo" :label="t('monitor.colReportNo')" min-width="150" />
          <el-table-column prop="enterpriseName" :label="t('monitor.colEnterpriseName')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="accountingPeriod" :label="t('monitor.colYear')" min-width="100" />
          <el-table-column prop="totalEmission" :label="t('monitor.colTotalEmission')" min-width="140">
            <template #default="{ row }">{{ formatNumber(row.totalEmission) }}</template>
          </el-table-column>
          <el-table-column prop="status" :label="t('monitor.colStatus')" min-width="110">
            <template #default="{ row }">
              <StatusTag :status="row.status" domain="report" />
            </template>
          </el-table-column>
          <el-table-column prop="submitTime" :label="t('monitor.colSubmitTime')" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.submitTime || row.createdAt) }}</template>
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
    </section>
  </PageContainer>
</template>

<style scoped>
.monitor-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.card-header {
  font-weight: 600;
  font-size: var(--text-md);
}

.org-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px 18px;
  margin-bottom: 12px;
}

.contact-form,
.search-form {
  display: flex;
  flex-wrap: wrap;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  padding: 10px 0;
}

.stat-card {
  border-radius: var(--radius-card);
  padding: 20px;
  color: var(--color-text);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-top: 3px solid var(--color-border);
}

/* 顶部 3px 语义色条（不用左边框，规避"colored border-left"反模式） */
.stat-card.total {
  border-top-color: var(--color-chart-1);
}

.stat-card.pending {
  border-top-color: var(--color-status-warning);
}

.stat-card.approved {
  border-top-color: var(--color-status-success);
}

.stat-card.rejected {
  border-top-color: var(--color-status-danger);
}

.stat-card.rate {
  border-top-color: var(--color-accent);
}

.stat-label {
  font-size: var(--text-base);
  margin-bottom: 8px;
  color: var(--color-text-muted);
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  color: var(--color-text);
}

.pagination-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.stats-dials-row {
  display: flex;
  justify-content: center;
  gap: 48px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.dial-card {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.dial-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.dial-val {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.dial-lbl {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
</style>
