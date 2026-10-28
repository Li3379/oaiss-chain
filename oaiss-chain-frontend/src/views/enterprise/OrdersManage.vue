<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳交易订单生命周期管理 (Orders Manage)
 * 【路由地址】：/enterprise/orders/manage
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 交易订单检索栏：按交易订单号、交易类型（双向拍卖/P2P定向转让）、日期区间多维过滤
 *   2. 订单状态统计标签：待撮合/待确认、已完成、已取消订单分类展示
 *   3. 订单列表表格：展示买卖方向、配额数量、成交单价、总金额、创建时间与订单状态
 *   4. 订单撤回操作：未成交或待对方确认的订单可点击【撤单】按钮取消，释放锁定配额
 *   5. 交易对手详情弹窗：查看买卖双方企业主体基本信息
 * 【对应后端 Controller & API】：
 *   - TradeController:
 *     * GET  /api/v1/trade/my-trades     -> getMyTrades() 查询企业参与的订单
 *     * POST /api/v1/trade/{id}/cancel   -> cancelTrade() 撤销未成交订单
 * ==============================================================================
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyTrades, cancelTrade } from '../../api/trade'
import type { TradeResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'

const { t } = useI18n()

interface OrdersSearchForm {
  tradeNo: string
  tradeType: number | ''
  dateRange: string[]
}

const searchForm = reactive<OrdersSearchForm>({
  tradeNo: '',
  tradeType: '',
  dateRange: [],
})

const tradeTypeOptions: Array<{ label: string; value: number | '' }> = [
  { label: 'common.all', value: '' },
  { label: 'ordersManage.tradeTypeAuction', value: 1 },
  { label: 'ordersManage.tradeTypeP2P', value: 2 },
]

const tableData = ref<TradeResponse[]>([])
const loading = ref(false)
const selectedRows = ref<TradeResponse[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const detailDialogVisible = ref(false)
const currentDetailTrade = ref<TradeResponse | null>(null)

const loadTrades = async () => {
  try {
    loading.value = true
    const [startDate, endDate] = searchForm.dateRange || []
    const params: Record<string, unknown> = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      tradeType: searchForm.tradeType || undefined,
      tradeNo: searchForm.tradeNo.trim() || undefined,
    }
    if (startDate) params.startTime = `${startDate} 00:00:00`
    if (endDate) params.endTime = `${endDate} 23:59:59`
    const data = await getMyTrades(params)
    tableData.value = data?.items || []
    total.value = data?.total || 0
  } catch (error) {
    ElMessage.error(t('ordersManage.loadFailed'))
  } finally {
    loading.value = false
  }
}

const onQuery = () => {
  currentPage.value = 1
  loadTrades()
}

const onReset = () => {
  searchForm.tradeNo = ''
  searchForm.tradeType = ''
  searchForm.dateRange = []
  currentPage.value = 1
  loadTrades()
}

const onSelectionChange = (rows: TradeResponse[]) => {
  selectedRows.value = rows
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadTrades()
}

const onCurrentChange = (page: number) => {
  currentPage.value = page
  loadTrades()
}

/** 交易类型文案走前端 i18n（后端 tradeTypeText 为中文，en 界面下会混语言） */
const tradeTypeLabel = (tradeType: number | null | undefined) => {
  if (tradeType === 1) return t('ordersManage.tradeTypeAuction')
  if (tradeType === 2) return t('ordersManage.tradeTypeP2P')
  return '-'
}

const getCounterparty = (row: TradeResponse) => {
  return row.buyerName || row.sellerName || '-'
}

const openDetail = (row: TradeResponse) => {
  currentDetailTrade.value = row
  detailDialogVisible.value = true
}

const handleCancel = async (row: TradeResponse) => {
  try {
    await ElMessageBox.confirm(t('ordersManage.confirmCancel'), t('ordersManage.cancelTrade'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })

    await cancelTrade(row.id)
    ElMessage.success(t('ordersManage.cancelSuccess'))
    loadTrades()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('ordersManage.cancelFailed'))
    }
  }
}

onMounted(() => {
  loadTrades()
})
</script>

<template>
  <section class="orders-page">
    <el-card class="section-card" shadow="never">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>{{ t('ordersManage.breadcrumbTrading') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ t('ordersManage.breadcrumbOrders') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </el-card>

    <PageHint :text="t('pageHints.ordersManage')" />

    <el-card class="section-card" shadow="never">
      <el-form :inline="true" class="search-form">
        <el-form-item :label="t('ordersManage.colTradeNo')">
          <el-input v-model="searchForm.tradeNo" :placeholder="t('ordersManage.placeholderTradeNo')" clearable />
        </el-form-item>

        <el-form-item :label="t('ordersManage.colTradeType')">
          <el-select v-model="searchForm.tradeType" :placeholder="t('ordersManage.placeholderTradeType')" clearable style="width: 150px">
            <el-option v-for="item in tradeTypeOptions" :key="item.value" :label="t(item.label)" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('ordersManage.dateRange')">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            :range-separator="t('ordersManage.dateSeparator')"
            :start-placeholder="t('ordersManage.startDate')"
            :end-placeholder="t('ordersManage.endDate')"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
          <el-button @click="onReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="section-card" shadow="never">
      <div class="table-tip">{{ t('common.total', { count: total }) }}</div>
      <el-table :data="tableData" border v-loading="loading" :empty-text="t('ordersManage.emptyText')" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="56" />
        <el-table-column :label="t('tradingMarket.colIndex')" width="80">
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="tradeNo" :label="t('ordersManage.colTradeNo')" min-width="180" />
        <el-table-column prop="tradeType" :label="t('ordersManage.colTradeType')" min-width="100">
          <template #default="{ row }">{{ tradeTypeLabel(row.tradeType) }}</template>
        </el-table-column>
        <el-table-column :label="t('ordersManage.colCounterparty')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getCounterparty(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" :label="t('ordersManage.colQuantity')" min-width="120">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" :label="t('ordersManage.colUnitPrice')" min-width="120">
          <template #default="{ row }">{{ formatNumber(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="t('ordersManage.colTotalAmount')" min-width="120">
          <template #default="{ row }">{{ formatNumber(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="statusText" :label="t('ordersManage.colTradeStatus')" min-width="100">
          <template #default="{ row }">
            <StatusTag domain="trade" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('ordersManage.colCreateTime')" min-width="170" :formatter="(row: TradeResponse) => formatDateTime(row.createdAt)" />
        <el-table-column :label="t('ordersManage.colOperation')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('common.viewDetail') }}</el-button>
            <el-button v-if="row.status === 0" link type="danger" @click="handleCancel(row)">{{ t('ordersManage.btnCancel') }}</el-button>
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

    <el-dialog v-model="detailDialogVisible" :title="t('ordersManage.dialogDetail')" width="700px">
      <el-descriptions :column="2" border v-if="currentDetailTrade">
        <el-descriptions-item :label="t('ordersManage.colTradeNo')">{{ currentDetailTrade.tradeNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.colTradeType')">{{ tradeTypeLabel(currentDetailTrade.tradeType) }}</el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.labelBuyer')">{{ currentDetailTrade.buyerName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.labelSeller')">{{ currentDetailTrade.sellerName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.colQuantity')">{{ formatNumber(currentDetailTrade.quantity) }}</el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.colUnitPrice')">{{ formatNumber(currentDetailTrade.unitPrice) }}</el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.colTotalAmount')">{{ formatNumber(currentDetailTrade.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.colTradeStatus')">
          <StatusTag domain="trade" :status="currentDetailTrade.status" />
        </el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.colCreateTime')" :span="2">{{ formatDateTime(currentDetailTrade.createdAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('ordersManage.labelRemark')" :span="2">{{ currentDetailTrade.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="currentDetailTrade.blockchainTxHash" :label="t('ordersManage.labelBlockchainHash')" :span="2">
          {{ currentDetailTrade.blockchainTxHash }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </section>
</template>

<style scoped>
.orders-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
}

.table-tip {
  margin-bottom: 10px;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.pagination-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
