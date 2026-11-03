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
 *   5. 全市场交易流水穿透台账：交易笔数/总量/总额聚合 + 分页流水明细（只读，无任何写操作）
 *   6. 监管职责与合规标准释义（PageHint / StatusTag）
 * 【对应后端 Controller & API】：
 *   - ThirdPartyController:
 *     * GET /api/v1/third-party/statistics     -> getStatistics() 监管宏观统计指标（含交易维度聚合）
 *     * GET /api/v1/third-party/org-info       -> getOrgInfo() 获取监管机构档案
 *     * GET /api/v1/third-party/carbon-reports -> getCarbonReports() 穿透查询企业碳报告
 *   - TradeController:
 *     * GET /api/v1/trade/list                 -> listTrades() 穿透查询全市场交易流水
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Clock, DataAnalysis, Money, OfficeBuilding, TrendCharts, Warning } from '@element-plus/icons-vue'
import { getCarbonReports, getOrgInfo, getStatistics } from '../../api/thirdParty'
import { listTrades } from '../../api/trade'
import { listFindings, resolveFinding, type RegulatoryFindingRecord } from '../../api/regulatoryFinding'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'
import type { CarbonReport, ThirdPartyOrg, ThirdPartyStatistics, TradeResponse } from '../../types'

const { t } = useI18n()

const statistics = ref<ThirdPartyStatistics>({
  totalReports: 0,
  pendingReports: 0,
  approvedReports: 0,
  rejectedReports: 0,
  totalTrades: 0,
  totalTradeQuantity: 0,
  totalTradeAmount: 0,
})

const orgInfo = ref<ThirdPartyOrg | null>(null)
const orgLoading = ref(false)

const searchForm = reactive<{
  enterpriseId: string
  keyword: string
  status: '' | number
}>({
  enterpriseId: '',
  keyword: '',
  status: '',
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

/** 首屏统计条：全部由已加载的监管统计接口返回值推导，不新增接口请求 */
const overviewStats = computed(() => {
  const s = statistics.value
  return [
    {
      label: t('monitor.statTotalReports'),
      value: s.totalReports,
      unit: t('monitor.statUnitReports'),
      delta: t('monitor.statTotalHint'),
      tone: 'neu' as const,
      icon: DataAnalysis,
    },
    {
      label: t('monitor.statPending'),
      value: s.pendingReports,
      unit: t('monitor.statUnitReports'),
      delta: t('monitor.statPendingHint', { count: numericPendingRate.value }),
      tone: s.pendingReports > 0 ? ('neu' as const) : ('pos' as const),
      icon: Clock,
    },
    {
      label: t('monitor.statApproved'),
      value: s.approvedReports,
      unit: t('monitor.statUnitReports'),
      delta: t('monitor.statApprovedHint'),
      tone: 'pos' as const,
      icon: CircleCheck,
    },
    {
      label: t('monitor.statApprovalRate'),
      value: approvalRate.value,
      unit: '',
      delta: t('monitor.statApprovalRateHint', { rejected: s.rejectedReports }),
      tone: s.rejectedReports > 0 ? ('neu' as const) : ('pos' as const),
      icon: TrendCharts,
    },
  ]
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
      totalTrades: result?.totalTrades || 0,
      totalTradeQuantity: Number(result?.totalTradeQuantity) || 0,
      totalTradeAmount: Number(result?.totalTradeAmount) || 0,
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


const onSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadReports()
}

const onCurrentChange = (page: number) => {
  currentPage.value = page
  loadReports()
}

/* ---------- 交易流水穿透台账（监管只读） ---------- */
const tradeForm = reactive<{ status: '' | number; tradeType: '' | number }>({
  status: '',
  tradeType: '',
})

const trades = ref<TradeResponse[]>([])
const tradesLoading = ref(false)
const tradePage = ref(1)
const tradePageSize = ref(10)
const tradeTotal = ref(0)

const loadTrades = async () => {
  try {
    tradesLoading.value = true
    // 监管端走 /trade/list（TradeController 已授权 THIRD_PARTY），返回含买卖双方名称与上链哈希的完整明细。
    const result = await listTrades({
      page: tradePage.value,
      size: tradePageSize.value,
      status: tradeForm.status === '' ? undefined : tradeForm.status,
      tradeType: tradeForm.tradeType === '' ? undefined : tradeForm.tradeType,
    })
    trades.value = result?.items || []
    tradeTotal.value = result?.total || 0
  } catch {
    ElMessage.error(t('monitor.tradeLoadFailed'))
  } finally {
    tradesLoading.value = false
  }
}

const onTradeSearch = () => {
  tradePage.value = 1
  loadTrades()
}

const onTradeReset = () => {
  tradeForm.status = ''
  tradeForm.tradeType = ''
  tradePage.value = 1
  loadTrades()
}

const onTradeSizeChange = (size: number) => {
  tradePageSize.value = size
  tradePage.value = 1
  loadTrades()
}

const onTradeCurrentChange = (page: number) => {
  tradePage.value = page
  loadTrades()
}

/** 交易类型文案走前端 i18n（后端 tradeTypeText 为中文，en 界面下会混语言） */
const tradeTypeLabel = (tradeType: number | null | undefined) => {
  if (tradeType === 1) return t('monitor.tradeTypeAuction')
  if (tradeType === 2) return t('monitor.tradeTypeP2P')
  if (tradeType === 3) return t('monitor.tradeTypeAllocation')
  return '-'
}

/* ---------- 监管线索（系统产出的异常发现，只读 + 处置标记） ---------- */
const findings = ref<RegulatoryFindingRecord[]>([])
const findingsLoading = ref(false)
const findingPage = ref(1)
const findingPageSize = ref(10)
const findingTotal = ref(0)

const loadFindings = async () => {
  try {
    findingsLoading.value = true
    const result = await listFindings({
      pageNum: findingPage.value,
      pageSize: findingPageSize.value,
    })
    findings.value = result?.items || []
    findingTotal.value = result?.total || 0
  } catch {
    ElMessage.error(t('monitor.findingLoadFailed'))
  } finally {
    findingsLoading.value = false
  }
}

const onFindingSizeChange = (size: number) => {
  findingPageSize.value = size
  findingPage.value = 1
  loadFindings()
}

const onFindingCurrentChange = (p: number) => {
  findingPage.value = p
  loadFindings()
}

/**
 * 标记线索已处置。
 *
 * 这不是"处置企业"——线索处置不改变任何业务状态（不冻结、不扣分、不下市），
 * 只是记录监管已看过这条发现。真正影响企业状态的动作归管理员。
 */
const onResolveFinding = async (row: RegulatoryFindingRecord) => {
  try {
    await ElMessageBox.confirm(
      t('monitor.findingResolveConfirm'),
      t('monitor.findingResolveTitle'),
      { type: 'info' },
    )
  } catch {
    return
  }
  try {
    await resolveFinding(row.id)
    ElMessage.success(t('monitor.findingResolveSuccess'))
    await loadFindings()
  } catch {
    ElMessage.error(t('monitor.findingResolveFailed'))
  }
}

const severityType = (severity: string) => {
  if (severity === 'CRITICAL') return 'danger'
  if (severity === 'HIGH') return 'warning'
  return 'info'
}

const ruleLabel = (ruleCode: string) => {
  const map: Record<string, string> = {
    REPEATED_REJECTION: t('monitor.ruleRepeatedRejection'),
    QUOTA_OVERDRAFT: t('monitor.ruleQuotaOverdraft'),
    CREDIT_PLUNGE: t('monitor.ruleCreditPlunge'),
    WASH_TRADING_BLOCKED: t('monitor.ruleWashTrading'),
  }
  return map[ruleCode] ?? ruleCode
}

onMounted(() => {
  loadStatistics()
  loadOrgInfo()
  loadReports()
  loadTrades()
  loadFindings()
})
</script>

<template>
  <section class="monitor-page pen-enter">
    <PageHeader
      :title="t('monitor.title')"
      :description="t('monitor.description')"
      :crumbs="[t('monitor.breadcrumbMonitorCenter'), t('monitor.breadcrumbMonitorPanel')]"
    />

    <PageHint :text="t('pageHints.monitor')" />

    <StatStrip :items="overviewStats" :loading="statsLoading" />

    <DataPanel
      :title="t('monitor.dataStats')"
      :meta="t('monitor.statApprovalRateHint', { rejected: statistics.rejectedReports })"
    >
      <div class="stats-dials-row" v-loading="statsLoading">
        <div class="dial-card">
          <el-progress type="dashboard" :percentage="numericApprovalRate" :color="'var(--color-accent)'" :width="110">
            <template #default="{ percentage }">
              <div class="dial-inner">
                <span class="dial-val">{{ percentage }}%</span>
                <span class="dial-lbl">{{ t('monitor.statApprovalRate') }}</span>
              </div>
            </template>
          </el-progress>
        </div>
        <div class="dial-card">
          <el-progress type="dashboard" :percentage="numericPendingRate" :color="'var(--color-status-warning)'" :width="110">
            <template #default="{ percentage }">
              <div class="dial-inner">
                <span class="dial-val">{{ percentage }}%</span>
                <span class="dial-lbl">{{ t('monitor.statPending') }}</span>
              </div>
            </template>
          </el-progress>
        </div>
      </div>
    </DataPanel>

    <DataPanel
      :title="t('monitor.orgInfoTitle')"
      :meta="t('monitor.orgFieldHint')"
      :icon="OfficeBuilding"
    >
      <div v-loading="orgLoading" class="org-body">
        <div v-if="orgInfo" class="org-grid">
          <div><strong>{{ t('monitor.orgName') }}:</strong> {{ orgInfo.orgName || '-' }}</div>
          <div><strong>{{ t('monitor.accessLevel') }}:</strong> {{ orgInfo.accessLevel || '-' }}</div>
          <div><strong>{{ t('monitor.address') }}:</strong> {{ orgInfo.address || '-' }}</div>
          <div><strong>{{ t('monitor.contactPerson') }}:</strong> {{ orgInfo.contactPerson || '-' }}</div>
          <div><strong>{{ t('monitor.contactPhone') }}:</strong> {{ orgInfo.contactPhone || '-' }}</div>
        </div>
        <!-- 联系人编辑表单已移除：监管角色按方案 A 定稿为「只读监督 + 定期核查」，
             不参与任何业务写。修改联系方式属平台运维职责，由管理员承担。 -->
      </div>
    </DataPanel>

    <FilterBar :aria-label="t('monitor.filterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('monitor.enterpriseIdLabel') }}</span>
        <el-input v-model="searchForm.enterpriseId" :placeholder="t('monitor.enterpriseIdPlaceholder')" clearable />
      </label>
      <label class="pen-field">
        <span class="pen-field__label">{{ t('common.keyword') }}</span>
        <el-input v-model="searchForm.keyword" :placeholder="t('common.enterKeyword')" clearable />
      </label>
      <label class="pen-field">
        <span class="pen-field__label">{{ t('monitor.colStatus') }}</span>
        <el-select v-model="searchForm.status" clearable style="width: 160px">
          <el-option :label="t('monitor.statusDraft')" :value="0" />
          <el-option :label="t('monitor.statusPending')" :value="1" />
          <el-option :label="t('monitor.statusApproved')" :value="3" />
          <el-option :label="t('monitor.statusRejected')" :value="4" />
          <el-option :label="t('monitor.statusOnChain')" :value="5" />
        </el-select>
      </label>
      <el-button type="primary" @click="onSearch">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </FilterBar>

    <DataPanel
      :title="t('monitor.tableTitle')"
      :meta="t('monitor.panelMeta', { total })"
    >
      <el-table :data="reports" v-loading="loading">
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

    <!-- 交易流水穿透台账：监管只读，不含任何写操作入口 -->
    <FilterBar :aria-label="t('monitor.tradeFilterLabel')">
      <label class="pen-field">
        <span class="pen-field__label">{{ t('monitor.colTradeStatus') }}</span>
        <el-select v-model="tradeForm.status" clearable style="width: 160px">
          <el-option :label="t('statusMeta.tradePending')" :value="0" />
          <el-option :label="t('statusMeta.tradeProcessing')" :value="1" />
          <el-option :label="t('statusMeta.tradeCompleted')" :value="2" />
          <el-option :label="t('statusMeta.tradeCancelled')" :value="3" />
          <el-option :label="t('statusMeta.tradeFailed')" :value="4" />
        </el-select>
      </label>
      <label class="pen-field">
        <span class="pen-field__label">{{ t('monitor.colTradeType') }}</span>
        <el-select v-model="tradeForm.tradeType" clearable style="width: 160px">
          <el-option :label="t('monitor.tradeTypeAuction')" :value="1" />
          <el-option :label="t('monitor.tradeTypeP2P')" :value="2" />
          <el-option :label="t('monitor.tradeTypeAllocation')" :value="3" />
        </el-select>
      </label>
      <el-button type="primary" @click="onTradeSearch">{{ t('common.search') }}</el-button>
      <el-button @click="onTradeReset">{{ t('common.reset') }}</el-button>
    </FilterBar>

    <DataPanel
      :title="t('monitor.tradeTableTitle')"
      :meta="t('monitor.tradePanelMeta', { total: tradeTotal })"
      :icon="Money"
    >
      <div class="trade-aggregates">
        <span>{{ t('monitor.tradeAggCount') }}：<strong>{{ formatNumber(statistics.totalTrades || 0) }}</strong></span>
        <span>{{ t('monitor.tradeAggVolume') }}：<strong>{{ formatNumber(statistics.totalTradeQuantity) }}</strong> tCO₂e</span>
        <span>{{ t('monitor.tradeAggAmount') }}：<strong>{{ formatNumber(statistics.totalTradeAmount) }}</strong> {{ t('monitor.tradeAmountUnit') }}</span>
      </div>

      <el-table :data="trades" v-loading="tradesLoading">
        <el-table-column prop="tradeNo" :label="t('monitor.colTradeNo')" min-width="170" show-overflow-tooltip />
        <el-table-column prop="sellerName" :label="t('monitor.colSeller')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="buyerName" :label="t('monitor.colBuyer')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="tradeType" :label="t('monitor.colTradeType')" min-width="110">
          <template #default="{ row }">{{ tradeTypeLabel(row.tradeType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" :label="t('monitor.colTradeQuantity')" min-width="120">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" :label="t('monitor.colTradeUnitPrice')" min-width="110">
          <template #default="{ row }">{{ formatNumber(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="t('monitor.colTradeTotal')" min-width="130">
          <template #default="{ row }">{{ formatNumber(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('monitor.colTradeStatus')" min-width="105">
          <template #default="{ row }">
            <StatusTag :status="row.status" domain="trade" />
          </template>
        </el-table-column>
        <el-table-column prop="blockchainTxHash" :label="t('monitor.colTradeHash')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.blockchainTxHash || '-' }}</template>
        </el-table-column>
        <el-table-column prop="completedAt" :label="t('monitor.colTradeCompletedAt')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.completedAt) || '-' }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-pagination
          v-model:current-page="tradePage"
          v-model:page-size="tradePageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="tradeTotal"
          @size-change="onTradeSizeChange"
          @current-change="onTradeCurrentChange"
        />
      </template>
    </DataPanel>

    <!-- 监管线索：系统定期核查产出的异常发现。只读 + 处置标记，不改任何业务状态。 -->
    <DataPanel
      :title="t('monitor.findingTableTitle')"
      :meta="t('monitor.findingPanelMeta', { total: findingTotal })"
      :icon="Warning"
    >
      <div class="finding-note">
        {{ t('monitor.findingBoundaryNote') }}
      </div>

      <el-table :data="findings" v-loading="findingsLoading">
        <el-table-column :label="t('common.colIndex')" width="80">
          <template #default="scope">
            {{ (findingPage - 1) * findingPageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="enterpriseId" :label="t('monitor.colFindingEnterprise')" min-width="120" />
        <el-table-column :label="t('monitor.colFindingRule')" min-width="170">
          <template #default="{ row }">{{ ruleLabel(row.ruleCode) }}</template>
        </el-table-column>
        <el-table-column :label="t('monitor.colFindingSeverity')" min-width="110">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)" size="small">
              {{ row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="evidence" :label="t('monitor.colFindingEvidence')" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">{{ row.evidence || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('monitor.colFindingCreatedAt')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" min-width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onResolveFinding(row)">
              {{ t('monitor.findingResolve') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-pagination
          v-model:current-page="findingPage"
          v-model:page-size="findingPageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="findingTotal"
          @size-change="onFindingSizeChange"
          @current-change="onFindingCurrentChange"
        />
      </template>
    </DataPanel>
  </section>
</template>

<style scoped>
.monitor-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.org-body {
  padding: var(--space-4);
}

.org-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-2) var(--space-5);
  margin-bottom: var(--space-3);
  color: var(--pen-muted);
  font-size: var(--fs-body);
}

.contact-form {
  display: flex;
  flex-wrap: wrap;
}

.stats-dials-row {
  display: flex;
  justify-content: center;
  gap: var(--space-7);
  padding: var(--space-4);
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
  font-size: var(--fs-stat);
  font-weight: 500;
  color: var(--pen-ink);
  font-variant-numeric: tabular-nums;
}

.dial-lbl {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
  margin-top: var(--space-0-5);
}

.trade-aggregates {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-5);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--pen-border);
  color: var(--pen-muted);
  font-size: var(--fs-sub);
}

.finding-note {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--pen-border);
  color: var(--pen-muted);
  font-size: var(--fs-sub);
}

.trade-aggregates strong {
  color: var(--pen-ink);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
</style>
