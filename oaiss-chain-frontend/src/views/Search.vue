<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：全站多维数据综合搜索中心 (Unified Search Center)
 * 【路由地址】：/search
 * 【访问权限】：企业 / 审核员 / 第三方监管 / 管理员（四角色均可访问）
 * 【界面可见功能】：
 *   1. 双 Tab：碳报告搜索 / 交易搜索，均为只读检索，不含任何写操作
 *   2. 碳报告检索：按关键词、企业 ID、报告状态过滤，分页呈现
 *   3. 交易检索：按交易类型、成交状态过滤，分页呈现买卖双方、数量、单价、总额、状态
 *   4. 匿名化提示：企业/审核员角色下买卖双方名称为稳定脱敏别名（需求 3.3.2），
 *      管理员/第三方监管可见真实企业名称用于穿透核查
 * 【对应后端 Controller & API】：
 *   - SearchController:
 *     * GET /api/v1/search/reports -> searchReports() 碳报告历史检索
 *     * GET /api/v1/search/trades  -> searchTrades() 交易流水检索（按角色脱敏）
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Document, Tickets } from '@element-plus/icons-vue'
import { searchReports, searchTrades } from '../api/search'
import { useAppStore } from '../store'
import { ROLE } from '../config/menu'
import { formatDateTime, formatNumber } from '../utils/format'
import PageHeader from '../components/pen/PageHeader.vue'
import FilterBar from '../components/pen/FilterBar.vue'
import DataPanel from '../components/pen/DataPanel.vue'
import PageHint from '../components/PageHint.vue'
import StatusTag from '../components/StatusTag.vue'
import type { CarbonReportSummary, TradeSummary } from '../types'

const { t } = useI18n()
const appStore = useAppStore()

const activeTab = ref<'reports' | 'trades'>('reports')

/** 企业/审核员检索到的是脱敏别名；管理员/第三方监管可见真实主体名称 */
const namesAreMasked = computed(
  () => appStore.role === ROLE.ENTERPRISE || appStore.role === ROLE.REVIEWER,
)

/* ---------- 碳报告搜索 ---------- */
const reportForm = reactive<{ keyword: string; enterpriseId: string; status: '' | number }>({
  keyword: '',
  enterpriseId: '',
  status: '',
})

const reports = ref<CarbonReportSummary[]>([])
const reportsLoading = ref(false)
const reportPage = ref(1)
const reportPageSize = ref(10)
const reportTotal = ref(0)

const loadReports = async () => {
  try {
    reportsLoading.value = true
    const enterpriseIdValue = reportForm.enterpriseId.trim()
    const enterpriseId = enterpriseIdValue ? Number(enterpriseIdValue) : undefined
    const result = await searchReports({
      pageNum: reportPage.value,
      pageSize: reportPageSize.value,
      keyword: reportForm.keyword.trim() || undefined,
      enterpriseId: Number.isFinite(enterpriseId) ? enterpriseId : undefined,
      status: reportForm.status === '' ? undefined : reportForm.status,
    })
    reports.value = result?.items || []
    reportTotal.value = result?.total || 0
  } catch {
    ElMessage.error(t('searchCenter.loadReportsFailed'))
  } finally {
    reportsLoading.value = false
  }
}

const onReportSearch = () => {
  reportPage.value = 1
  loadReports()
}

const onReportReset = () => {
  reportForm.keyword = ''
  reportForm.enterpriseId = ''
  reportForm.status = ''
  reportPage.value = 1
  loadReports()
}

const onReportSizeChange = (size: number) => {
  reportPageSize.value = size
  reportPage.value = 1
  loadReports()
}

const onReportCurrentChange = (page: number) => {
  reportPage.value = page
  loadReports()
}

/* ---------- 交易搜索 ---------- */
const tradeForm = reactive<{ tradeType: '' | number; status: '' | number }>({
  tradeType: '',
  status: '',
})

const trades = ref<TradeSummary[]>([])
const tradesLoading = ref(false)
const tradePage = ref(1)
const tradePageSize = ref(10)
const tradeTotal = ref(0)

const loadTrades = async () => {
  try {
    tradesLoading.value = true
    const result = await searchTrades({
      pageNum: tradePage.value,
      pageSize: tradePageSize.value,
      tradeType: tradeForm.tradeType === '' ? undefined : tradeForm.tradeType,
      status: tradeForm.status === '' ? undefined : tradeForm.status,
    })
    trades.value = result?.items || []
    tradeTotal.value = result?.total || 0
  } catch {
    ElMessage.error(t('searchCenter.loadTradesFailed'))
  } finally {
    tradesLoading.value = false
  }
}

const onTradeSearch = () => {
  tradePage.value = 1
  loadTrades()
}

const onTradeReset = () => {
  tradeForm.tradeType = ''
  tradeForm.status = ''
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

/** 交易类型文案走前端 i18n（后端不返回 tradeTypeText，避免 en 界面混入中文） */
const tradeTypeLabel = (tradeType: number | null | undefined) => {
  if (tradeType === 1) return t('searchCenter.tradeTypeAuction')
  if (tradeType === 2) return t('searchCenter.tradeTypeP2P')
  if (tradeType === 3) return t('searchCenter.tradeTypeAllocation')
  return '-'
}

/** 第三方监管与管理员的检索口径说明（穿透实名） */
const regulatorHint = computed(() => t('searchCenter.hintRegulator'))

onMounted(() => {
  loadReports()
  loadTrades()
})
</script>

<template>
  <section class="search-page pen-enter">
    <PageHeader
      :title="t('searchCenter.title')"
      :description="t('searchCenter.description')"
      :crumbs="[t('searchCenter.breadcrumbCenter'), t('searchCenter.breadcrumbSearch')]"
    />

    <PageHint :text="t('pageHints.searchCenter')" :icon="Tickets" />

    <!-- 匿名化说明：企业/审核员必须能看出名称为脱敏值 -->
    <aside v-if="namesAreMasked" class="mask-note" role="note">
      <span class="mask-note__tag">{{ t('searchCenter.maskTag') }}</span>
      <span class="mask-note__text">{{ t('searchCenter.maskHint') }}</span>
    </aside>
    <aside v-else class="mask-note" role="note">
      <span class="mask-note__tag">{{ t('searchCenter.realNameTag') }}</span>
      <span class="mask-note__text">{{ regulatorHint }}</span>
    </aside>

    <el-tabs v-model="activeTab" class="search-tabs">
      <!-- 碳报告搜索 -->
      <el-tab-pane :label="t('searchCenter.tabReports')" name="reports">
        <FilterBar :aria-label="t('searchCenter.reportFilterLabel')">
          <label class="pen-field">
            <span class="pen-field__label">{{ t('common.keyword') }}</span>
            <el-input v-model="reportForm.keyword" :placeholder="t('common.enterKeyword')" clearable />
          </label>
          <label class="pen-field">
            <span class="pen-field__label">{{ t('searchCenter.enterpriseIdLabel') }}</span>
            <el-input v-model="reportForm.enterpriseId" :placeholder="t('searchCenter.enterpriseIdPlaceholder')" clearable />
          </label>
          <label class="pen-field">
            <span class="pen-field__label">{{ t('searchCenter.reportStatusLabel') }}</span>
            <el-select v-model="reportForm.status" clearable style="width: 160px">
              <el-option :label="t('searchCenter.reportStatusDraft')" :value="0" />
              <el-option :label="t('searchCenter.reportStatusSubmitted')" :value="1" />
              <el-option :label="t('searchCenter.reportStatusUnderReview')" :value="2" />
              <el-option :label="t('searchCenter.reportStatusApproved')" :value="3" />
              <el-option :label="t('searchCenter.reportStatusRejected')" :value="4" />
              <el-option :label="t('searchCenter.reportStatusOnChain')" :value="5" />
            </el-select>
          </label>
          <el-button type="primary" @click="onReportSearch">{{ t('common.search') }}</el-button>
          <el-button @click="onReportReset">{{ t('common.reset') }}</el-button>
        </FilterBar>

        <DataPanel
          :title="t('searchCenter.reportTableTitle')"
          :meta="t('searchCenter.reportPanelMeta', { total: reportTotal })"
          :icon="Document"
        >
          <el-table :data="reports" v-loading="reportsLoading">
            <el-table-column prop="reportNo" :label="t('searchCenter.colReportNo')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="title" :label="t('searchCenter.colReportTitle')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="enterpriseId" :label="t('searchCenter.colEnterpriseId')" min-width="110" />
            <el-table-column prop="accountingPeriod" :label="t('searchCenter.colAccountingPeriod')" min-width="120" />
            <el-table-column prop="totalEmission" :label="t('searchCenter.colTotalEmission')" min-width="140">
              <template #default="{ row }">{{ formatNumber(row.totalEmission) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('searchCenter.colReportStatus')" min-width="110">
              <template #default="{ row }">
                <StatusTag :status="row.status" domain="report" />
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" :label="t('searchCenter.colCreatedAt')" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>

          <template #footer>
            <el-pagination
              v-model:current-page="reportPage"
              v-model:page-size="reportPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="reportTotal"
              @size-change="onReportSizeChange"
              @current-change="onReportCurrentChange"
            />
          </template>
        </DataPanel>
      </el-tab-pane>

      <!-- 交易搜索 -->
      <el-tab-pane :label="t('searchCenter.tabTrades')" name="trades">
        <FilterBar :aria-label="t('searchCenter.tradeFilterLabel')">
          <label class="pen-field">
            <span class="pen-field__label">{{ t('searchCenter.tradeTypeLabel') }}</span>
            <el-select v-model="tradeForm.tradeType" clearable style="width: 160px">
              <el-option :label="t('searchCenter.tradeTypeAuction')" :value="1" />
              <el-option :label="t('searchCenter.tradeTypeP2P')" :value="2" />
              <el-option :label="t('searchCenter.tradeTypeAllocation')" :value="3" />
            </el-select>
          </label>
          <label class="pen-field">
            <span class="pen-field__label">{{ t('searchCenter.tradeStatusLabel') }}</span>
            <el-select v-model="tradeForm.status" clearable style="width: 160px">
              <el-option :label="t('statusMeta.tradePending')" :value="0" />
              <el-option :label="t('statusMeta.tradeProcessing')" :value="1" />
              <el-option :label="t('statusMeta.tradeCompleted')" :value="2" />
              <el-option :label="t('statusMeta.tradeCancelled')" :value="3" />
              <el-option :label="t('statusMeta.tradeFailed')" :value="4" />
            </el-select>
          </label>
          <el-button type="primary" @click="onTradeSearch">{{ t('common.search') }}</el-button>
          <el-button @click="onTradeReset">{{ t('common.reset') }}</el-button>
        </FilterBar>

        <DataPanel
          :title="t('searchCenter.tradeTableTitle')"
          :meta="t('searchCenter.tradePanelMeta', { total: tradeTotal })"
          :icon="Tickets"
        >
          <el-table :data="trades" v-loading="tradesLoading">
            <el-table-column prop="tradeNo" :label="t('searchCenter.colTradeNo')" min-width="170" show-overflow-tooltip />
            <el-table-column prop="sellerName" :label="t('searchCenter.colSeller')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="buyerName" :label="t('searchCenter.colBuyer')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="tradeType" :label="t('searchCenter.tradeTypeLabel')" min-width="110">
              <template #default="{ row }">{{ tradeTypeLabel(row.tradeType) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" :label="t('searchCenter.colQuantity')" min-width="120">
              <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
            </el-table-column>
            <el-table-column prop="unitPrice" :label="t('searchCenter.colUnitPrice')" min-width="110">
              <template #default="{ row }">{{ formatNumber(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column prop="totalAmount" :label="t('searchCenter.colTotalAmount')" min-width="130">
              <template #default="{ row }">{{ formatNumber(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('searchCenter.tradeStatusLabel')" min-width="105">
              <template #default="{ row }">
                <StatusTag :status="row.status" domain="trade" />
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" :label="t('searchCenter.colCreatedAt')" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
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
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.mask-note {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: 9px 12px;
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  background: var(--pen-surface);
  color: var(--pen-muted);
  font-size: var(--fs-sub);
  line-height: 1.6;
}

.mask-note__tag {
  flex-shrink: 0;
  padding: 0 var(--space-1-5);
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-pill);
  color: var(--pen-ink);
  font-size: var(--fs-caption);
  font-weight: 500;
}

.mask-note__text {
  flex: 1 1 auto;
  min-width: 0;
}

.search-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--space-4);
}
</style>
