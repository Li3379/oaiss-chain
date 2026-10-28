<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳配额集中竞价与双向拍卖大厅 (Double Auction Market)
 * 【路由地址】：/enterprise/trading/market
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 资产状态卡片：可用碳币资金余额、当前可用碳配额吨数、市场 AI 参考基准价
 *   2. 买卖委托报单面板：支持买入（申购）与卖出（出让）限价报单录入与提交防抖
 *   3. 订单簿深度盘口：实时呈现买一至买五、卖一至卖五挂单队列及撮合深度
 *   4. 我的挂单与成交历史：切换查看当前企业的委托报单及撮合成交出清记录
 *   5. AI 智能履约采购顾问：根据当前排碳和缺口推演采购时机与履约成本
 * 【对应后端 Controller & API】：
 *   - DoubleAuctionController:
 *     * POST /api/v1/auction/buy       -> submitBuyOrder() 提交买入委托挂单
 *     * POST /api/v1/auction/sell      -> submitSellOrder() 提交卖出委托挂单
 *     * GET  /api/v1/auction/orders    -> getAuctionOrders() 查询市场订单簿盘口
 *     * GET  /api/v1/auction/my-orders -> getMyOrders() 查询企业自身挂单
 *     * GET  /api/v1/auction/results   -> getMatchResults() 查询撮合成交结果
 *   - CarbonCoinController:
 *     * GET  /api/v1/carbon-coin/account -> getMyAccount() 获取碳币钱包资产
 * ==============================================================================
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Cpu, InfoFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormItemRule, FormRules } from 'element-plus'
import {
  getAuctionOrders,
  getMatchResults,
  getMyOrders,
  submitBuyOrder,
  submitSellOrder,
  getProcurementAdvice,
  type ProcurementAdviceResponse,
} from '../../api/auction'
import { getMyAccount } from '../../api/carbonCoin'
import { getEnterpriseInfo } from '../../api/enterprise'
import type { AuctionOrderRequest, AuctionOrderResponse, MatchingResultResponse } from '../../types'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHint from '../../components/PageHint.vue'
import StatusTag from '../../components/StatusTag.vue'

const { t } = useI18n()

interface TradingMarketForm {
  direction: number | null
  quantity: string
  price: string
}

const userBalance = ref(0)
const userQuota = ref(0)
const aiReferencePrice = ref('58.20')

const loadUserAssets = async () => {
  try {
    const acc = await getMyAccount()
    if (acc) userBalance.value = acc.balance || 0
  } catch {
    // fallback
  }
  try {
    const ent = await getEnterpriseInfo()
    if (ent) userQuota.value = Number(ent.carbonTradable) || 0
  } catch {
    // fallback
  }
}

const applyAiPrice = () => {
  formModel.price = aiReferencePrice.value
  ElMessage.success(t('tradingMarket.appliedAiPrice'))
}

const selectedRows = ref<AuctionOrderResponse[]>([])

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const activeTab = ref<'all' | 'my' | 'match'>('all')
const tableLoading = ref(false)

const formModel = reactive<TradingMarketForm>({
  direction: null,
  quantity: '',
  price: '',
})

const estimatedTotal = computed(() => {
  const q = parseFloat(formModel.quantity)
  const p = parseFloat(formModel.price)
  if (!Number.isFinite(q) || !Number.isFinite(p) || q <= 0 || p <= 0) return '0.00'
  return (q * p).toFixed(2)
})

const tableData = ref<AuctionOrderResponse[]>([])

const matchData = ref<MatchingResultResponse[]>([])
const matchLoading = ref(false)
const matchPage = ref(1)
const matchPageSize = ref(10)
const matchTotal = ref(0)

const formRules: FormRules<TradingMarketForm> = {
  direction: [{ required: true, message: t('tradingMarket.selectDirection'), trigger: 'change' }],
  quantity: [
    { required: true, message: t('tradingMarket.enterQuantity'), trigger: 'blur' },
    positiveNumberRule(t('tradingMarket.quantityPositive')),
  ],
  price: [
    { required: true, message: t('tradingMarket.enterPrice'), trigger: 'blur' },
    positiveNumberRule(t('tradingMarket.pricePositive')),
  ],
}

// HTML min 属性不拦截手输的 0/负数/科学计数法，必须用 validator 兜底
function positiveNumberRule(message: string): FormItemRule {
  return {
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      const n = Number.parseFloat(value)
      if (!Number.isFinite(n) || n <= 0) {
        callback(new Error(message))
      } else if (n > 1_000_000_000) {
        callback(new Error(t('tradingMarket.valueOutOfRange')))
      } else {
        callback()
      }
    },
    trigger: 'blur',
  }
}

const directionOptions = [
  { label: 'tradingMarket.buy', value: 1 },
  { label: 'tradingMarket.sell', value: 2 },
]

const fetchData = async () => {
  if (activeTab.value === 'match') {
    await fetchMatchResults()
    return
  }

  tableLoading.value = true
  try {
    const response = activeTab.value === 'my'
      ? await getMyOrders({ pageNum: page.value, pageSize: pageSize.value })
      : await getAuctionOrders({ pageNum: page.value, pageSize: pageSize.value })

    tableData.value = response.items
    total.value = response.total
  } catch {
    ElMessage.error(t('tradingMarket.fetchDataFailed'))
    tableData.value = []
    total.value = 0
  } finally {
    tableLoading.value = false
  }
}

const fetchMatchResults = async () => {
  matchLoading.value = true
  try {
    const result = await getMatchResults({
      pageNum: matchPage.value,
      pageSize: matchPageSize.value,
    })
    matchData.value = result.items
    matchTotal.value = result.total
  } catch {
    ElMessage.error(t('tradingMarket.fetchMatchFailed'))
    matchData.value = []
    matchTotal.value = 0
  } finally {
    matchLoading.value = false
  }
}

const onMatchSizeChange = (size: number) => {
  matchPageSize.value = size
  matchPage.value = 1
  fetchMatchResults()
}

const onMatchPageChange = (current: number) => {
  matchPage.value = current
  fetchMatchResults()
}

const onTabChange = () => {
  page.value = 1
  matchPage.value = 1
  fetchData()
}

const onQuery = () => {
  page.value = 1
  fetchData()
}

const goToOrders = () => {
  activeTab.value = 'all'
  page.value = 1
  fetchData()
}

const onSelectionChange = (rows: AuctionOrderResponse[]) => {
  selectedRows.value = rows
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  page.value = 1
  fetchData()
}

const onPageChange = (current: number) => {
  page.value = current
  fetchData()
}

const resetForm = () => {
  formModel.direction = null
  formModel.quantity = ''
  formModel.price = ''
}

const openAddDialog = () => {
  resetForm()
  loadUserAssets()
  dialogVisible.value = true
}

const getDirectionLabel = (direction: number) => {
  return direction === 1 ? t('tradingMarket.buy') : t('tradingMarket.sell')
}

const getDirectionType = (direction: number) => {
  return direction === 1 ? 'success' : 'danger'
}

const getStatusLabel = (status: number) => {
  const statusMap: Record<number, string> = {
    0: t('tradingMarket.statusPending'),
    1: t('tradingMarket.statusPartial'),
    2: t('tradingMarket.statusMatched'),
    3: t('tradingMarket.statusCancelled'),
  }
  return statusMap[status] || t('tradingMarket.statusUnknown')
}

const getStatusType = (status: number) => {
  const typeMap: Record<number, string> = {
    0: 'info',
    1: 'warning',
    2: 'success',
    3: 'info',
  }
  return typeMap[status] || 'info'
}

const submitting = ref(false)

const onSave = async () => {
  // 同步置位防抖：若放在 await validate() 之后，连点会在事件循环间隙全部穿透
  if (submitting.value) return
  submitting.value = true
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid || formModel.direction === null) {
      ElMessage.warning(t('tradingMarket.incompleteForm'))
      return
    }

    const orderData: AuctionOrderRequest = {
      direction: formModel.direction,
      quantity: Number.parseFloat(formModel.quantity),
      price: Number.parseFloat(formModel.price),
    }

    if (formModel.direction === 1) {
      await submitBuyOrder(orderData)
      ElMessage.success(`${t('tradingMarket.buySuccess')} ${t('nextSteps.afterTradeCreated')}`)
    } else {
      await submitSellOrder(orderData)
      ElMessage.success(`${t('tradingMarket.sellSuccess')} ${t('nextSteps.afterTradeCreated')}`)
    }

    dialogVisible.value = false
    await fetchData()
  } catch {
    ElMessage.error(t('tradingMarket.submitFailed'))
  } finally {
    submitting.value = false
  }
}

const onCancelDialog = () => {
  dialogVisible.value = false
  ElMessage.info(t('common.cancelled'))
}

const adviceData = ref<ProcurementAdviceResponse | null>(null)
const adviceLoading = ref(false)

const loadProcurementAdvice = async () => {
  try {
    adviceLoading.value = true
    const res = await getProcurementAdvice({
      current_emission: 50000,
      current_quota: userQuota.value || 38000,
      current_market_price: parseFloat(aiReferencePrice.value) || 58.20
    })
    adviceData.value = res
  } catch {
    // fallback silently
  } finally {
    adviceLoading.value = false
  }
}

onMounted(() => {
  fetchData()
  loadUserAssets().then(() => loadProcurementAdvice())
})
</script>

<template>
  <section class="market-page">
    <el-card class="section-card" shadow="never">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>{{ t('tradingMarket.breadcrumbTrading') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ t('tradingMarket.breadcrumbAuction') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </el-card>

    <PageHint :text="t('pageHints.tradingMarket')" />

    <!-- AI 智能履约与采购决策卡片 -->
    <el-card v-if="adviceData" class="section-card" shadow="never" style="border: 1px solid rgba(64, 158, 255, 0.28); background: linear-gradient(180deg, rgba(64, 158, 255, 0.04) 0%, #fafbfc 100%);">
      <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 16px;">
        <div style="flex: 1;">
          <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 8px;">
            <div style="display: inline-flex; align-items: center; gap: 6px;">
              <el-icon color="#409eff" :size="15"><Cpu /></el-icon>
              <strong style="font-size: 14px; color: #303133;">{{ t('tradingMarket.aiAdviceCardTitle') }}</strong>
            </div>
            <el-tag :type="adviceData.risk_level === 'HIGH' ? 'danger' : (adviceData.risk_level === 'MEDIUM' ? 'warning' : 'success')" size="small">
              {{ t('tradingMarket.riskLevel') }}: {{ adviceData.risk_level }}
            </el-tag>
            <span style="font-size: 12px; color: #909399;">缺口: {{ formatNumber(adviceData.shortfall_tons) }} 吨 ｜ 预估成本: {{ formatNumber(adviceData.estimated_cost) }} 元</span>
          </div>
          <p style="font-size: 13px; color: #606266; line-height: 1.6; margin: 0 0 8px;">
            {{ adviceData.advice }}
          </p>
          <div v-if="adviceData.action_items?.length" style="display: flex; flex-direction: column; gap: 4px;">
            <div v-for="(act, idx) in adviceData.action_items" :key="idx" style="font-size: 12px; color: #475569;">
              • {{ act }}
            </div>
          </div>
        </div>
        <el-button type="primary" size="small" @click="openAddDialog">
          {{ t('tradingMarket.dialogCreate') }}
        </el-button>
      </div>
    </el-card>

    <el-card class="section-card" shadow="never">
      <div class="search-row">
        <el-button type="primary" @click="onQuery">{{ t('common.search') }}</el-button>
        <el-button type="success" plain @click="openAddDialog">{{ t('tradingMarket.dialogCreate') }}</el-button>
      </div>
    </el-card>

    <el-card class="section-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane :label="t('tradingMarket.tabAll')" name="all" />
        <el-tab-pane :label="t('tradingMarket.tabMine')" name="my" />
        <el-tab-pane :label="t('tradingMarket.tabMatch')" name="match" />
      </el-tabs>

      <div v-if="activeTab !== 'match'" class="table-tip">{{ t('common.total', { count: total }) }}</div>

      <el-table
        v-if="activeTab !== 'match'"
        :data="tableData"
        :loading="tableLoading"
        border
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="56" />
        <el-table-column :label="t('tradingMarket.colIndex')" width="80">
          <template #default="scope">
            {{ (page - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column :label="t('tradingMarket.colDirection')" min-width="100">
          <template #default="{ row }">
            <el-tag :type="getDirectionType(row.direction)">
              {{ getDirectionLabel(row.direction) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" :label="t('tradingMarket.colQuantity')" min-width="120">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="price" :label="t('tradingMarket.colPrice')" min-width="130">
          <template #default="{ row }">{{ formatNumber(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('tradingMarket.colStatus')" min-width="110">
          <template #default="{ row }">
            <StatusTag :type="getStatusType(row.status)" :text="getStatusLabel(row.status)" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('tradingMarket.colCreateTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div v-if="activeTab !== 'match'" class="pager-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </div>

      <el-alert
        v-if="activeTab === 'match'"
        class="match-guide"
        :title="t('tradingMarket.matchGuideTitle')"
        :description="t('tradingMarket.matchGuideText')"
        type="info"
        show-icon
        :closable="false"
      >
        <template #default>
          <el-button type="primary" link size="small" @click="goToOrders">
            {{ t('tradingMarket.goToOrders') }}
          </el-button>
        </template>
      </el-alert>

      <el-table
        v-if="activeTab === 'match'"
        :data="matchData"
        :loading="matchLoading"
        border
      >
        <el-table-column :label="t('tradingMarket.colIndex')" width="80">
          <template #default="scope">
            {{ (matchPage - 1) * matchPageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="buyOrderId" :label="t('tradingMarket.colBuyId')" min-width="100" />
        <el-table-column prop="sellOrderId" :label="t('tradingMarket.colSellId')" min-width="100" />
        <el-table-column prop="matchedQuantity" :label="t('tradingMarket.colMatchQuantity')" min-width="130" />
        <el-table-column prop="settlementPrice" :label="t('tradingMarket.colMatchPrice')" min-width="140" />
        <el-table-column prop="settledAt" :label="t('tradingMarket.colMatchTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.settledAt) }}</template>
        </el-table-column>
      </el-table>

      <div v-if="activeTab === 'match'" class="pager-row">
        <el-pagination
          v-model:current-page="matchPage"
          v-model:page-size="matchPageSize"
          background
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="matchTotal"
          @size-change="onMatchSizeChange"
          @current-change="onMatchPageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="t('tradingMarket.dialogCreate')"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="100px" scroll-to-error>
        <el-form-item :label="t('tradingMarket.colDirection')" prop="direction">
          <el-select v-model="formModel.direction" :placeholder="t('tradingMarket.selectDirection')" style="width: 100%">
            <el-option
              v-for="item in directionOptions"
              :key="item.value"
              :label="t(item.label)"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('tradingMarket.colQuantity')" prop="quantity">
          <el-input
            v-model="formModel.quantity"
            type="number"
            :placeholder="t('tradingMarket.enterQuantity')"
            :min="0"
            :step="0.01"
          />
        </el-form-item>

        <el-form-item :label="t('tradingMarket.colPrice')" prop="price">
          <el-input
            v-model="formModel.price"
            type="number"
            :placeholder="t('tradingMarket.enterPrice')"
            :min="0"
            :step="0.01"
          />
        </el-form-item>

        <div style="margin: -10px 0 16px 100px; display: flex; align-items: center; justify-content: space-between;">
          <span style="font-size: 12px; color: #64748b; display: inline-flex; align-items: center; gap: 5px;">
            <el-icon :size="13" color="#14a79a"><InfoFilled /></el-icon>
            {{ t('tradingMarket.aiPriceTip', { price: aiReferencePrice }) }}
          </span>
          <el-button link type="primary" size="small" @click="applyAiPrice">
            {{ t('tradingMarket.btnApplyAiPrice') }}
          </el-button>
        </div>

        <!-- ELI5 预估总额与竞价规则卡片 -->
        <div class="auction-preview-card">
          <div class="preview-row">
            <span>{{ t('tradingMarket.estimatedTotal') }}:</span>
            <strong class="preview-total">{{ estimatedTotal }}</strong>
            <span class="preview-unit">碳币</span>
          </div>

          <!-- 买入资产联动卡片 -->
          <div v-if="formModel.direction === 1" style="margin-top: 10px; font-size: 13px; line-height: 1.8;">
            <div style="color: #606266; display: flex; justify-content: space-between;">
              <span>{{ t('tradingMarket.userBalance') }}:</span>
              <strong>{{ formatNumber(userBalance) }} 碳币</strong>
            </div>
            <div style="color: #606266; display: flex; justify-content: space-between;">
              <span>{{ t('tradingMarket.estimatedFreeze') }}:</span>
              <strong style="color: #e6a23c;">{{ estimatedTotal }} 碳币</strong>
            </div>
            <div v-if="parseFloat(estimatedTotal) > userBalance" style="margin-top: 6px;">
              <el-alert type="error" :closable="false" show-icon :title="t('tradingMarket.balanceInsufficientWarn')" />
            </div>
          </div>

          <!-- 卖出配额联动卡片 -->
          <div v-else-if="formModel.direction === 2" style="margin-top: 10px; font-size: 13px; line-height: 1.8;">
            <div style="color: #606266; display: flex; justify-content: space-between;">
              <span>{{ t('tradingMarket.userQuota') }}:</span>
              <strong>{{ formatNumber(userQuota) }} 吨</strong>
            </div>
            <div style="color: #606266; display: flex; justify-content: space-between;">
              <span>{{ t('tradingMarket.estimatedQuotaFreeze') }}:</span>
              <strong style="color: #e6a23c;">{{ formModel.quantity || '0' }} 吨</strong>
            </div>
            <div v-if="parseFloat(formModel.quantity || '0') > userQuota" style="margin-top: 6px;">
              <el-alert type="error" :closable="false" show-icon :title="t('tradingMarket.quotaInsufficientWarn')" />
            </div>
          </div>

          <div v-if="formModel.direction === 1" class="preview-tip preview-tip--buy" style="margin-top: 10px;">
            {{ t('tradingMarket.directionTipBuy') }}
          </div>
          <div v-else-if="formModel.direction === 2" class="preview-tip preview-tip--sell" style="margin-top: 10px;">
            {{ t('tradingMarket.directionTipSell') }}
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="onCancelDialog">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onSave">{{ t('tradingMarket.btnSubmit') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.market-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.search-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.table-tip {
  margin-bottom: 10px;
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.pager-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.auction-preview-card {
  margin-top: 10px;
  padding: 12px 14px;
  background: rgba(20, 167, 154, 0.08);
  border: 1px solid rgba(20, 167, 154, 0.25);
  border-radius: 8px;
}

.preview-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--text-sm);
  color: var(--text-primary);
}

.preview-total {
  font-size: 20px;
  color: var(--el-color-primary);
  font-weight: 700;
}

.preview-unit {
  color: var(--text-secondary);
  font-size: var(--text-xs);
}

.preview-tip {
  margin-top: 6px;
  font-size: var(--text-xs);
  line-height: 1.5;
}

.preview-tip--buy {
  color: var(--teal);
}

.preview-tip--sell {
  color: var(--el-color-warning);
}
</style>
