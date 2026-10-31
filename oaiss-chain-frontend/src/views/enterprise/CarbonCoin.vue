<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳币资产代币钱包 (Carbon Coin Account)
 * 【路由地址】：/enterprise/carbon-coin/account
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 碳币钱包资产卡片：展示可用余额、冻结金额与账户累计流动性
 * *   2. 资金交易流水明细表：分页展示充值、扣减与交易结算明细
 *   3. 碳币经济模型白话释义（PageHint / TermTip）
 * 【对应后端 Controller & API】：
 *   - CarbonCoinController:
 *     * GET  /api/v1/carbon-coin/account      -> getMyAccount() 获取钱包资产与余额
 *     * GET  /api/v1/carbon-coin/transactions -> getTransactions() 查询资金交易流水
 * * ==============================================================================
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CircleCheck, ShoppingCart, TrendCharts, Wallet } from '@element-plus/icons-vue'
import { getMyAccount, getTransactions } from '../../api/carbonCoin'
import TermTip from '../../components/TermTip.vue'
import PageHint from '../../components/PageHint.vue'
import { formatDateTime, formatNumber } from '../../utils/format'
import type { CarbonCoinAccountResponse, CarbonCoinTransaction } from '../../types'
import EmptyHint from '../../components/EmptyHint.vue'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'

const { t } = useI18n()

const accountData = ref<CarbonCoinAccountResponse | null>(null)
const accountLoading = ref(false)

const transactionData = ref<CarbonCoinTransaction[]>([])
const transactionLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const loadAccount = async () => {
  try {
    accountLoading.value = true
    const result = await getMyAccount()
    accountData.value = result
  } catch (error) {
    ElMessage.error(t('carbonCoin.loadAccountFailed'))
  } finally {
    accountLoading.value = false
  }
}

const loadTransactions = async () => {
  try {
    transactionLoading.value = true
    const result = await getTransactions({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
    transactionData.value = result?.items || []
    total.value = result?.total || 0
  } catch (error) {
    ElMessage.error(t('carbonCoin.loadRecordFailed'))
  } finally {
    transactionLoading.value = false
  }
}

const onSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadTransactions()
}
const onCurrentChange = (page: number) => {
  currentPage.value = page
  loadTransactions()
}

const getTransactionTypeTag = (type: number) => {
  const map: Record<number, string> = {
    1: 'success',
    2: 'danger',
    3: 'success',
    4: 'warning',
  }
  return map[type] || 'info'
}

const getTransactionTypeText = (type: number) => {
  const map: Record<number, string> = {
    1: t('carbonCoin.txTypeRecharge'),
    2: t('carbonCoin.txTypeBuy'),
    3: t('carbonCoin.txTypeSell'),
    4: t('carbonCoin.txTypeLegacy'),
  }
  return map[type] || t('common.status')
}

const getAccountStatusText = (status?: number) => (
  status === 1 ? t('carbonCoin.statusActive') : t('carbonCoin.statusInactive')
)

const getAmountClass = (type: number) => {
  const positiveTypes = [1, 3]
  return positiveTypes.includes(type) ? 'amount-positive' : 'amount-negative'
}

const formatAmount = (type: number, amount: number) => {
  const positiveTypes = [1, 3]
  const prefix = positiveTypes.includes(type) ? '+' : ''
  return prefix + amount
}

onMounted(() => {
  loadAccount()
  loadTransactions()
})

/** 首屏统计条：全部取自已加载的账户数据，不新增接口请求 */
const accountStats = computed(() => {
  const acc = accountData.value
  const statusText = getAccountStatusText(acc?.status)

  return [
    {
      label: t('carbonCoin.balance'),
      value: Number((acc?.balance || 0).toFixed(2)),
      unit: t('carbonCoin.unit'),
      delta: t('carbonCoin.statBalanceHint'),
      tone: 'neu' as const,
      icon: Wallet,
    },
    {
      label: t('carbonCoin.accountStatus'),
      value: statusText,
      unit: '',
      delta: t('carbonCoin.statStatusHint'),
      tone: acc?.status === 1 ? ('pos' as const) : ('neg' as const),
      icon: CircleCheck,
    },
    {
      label: t('carbonCoin.totalRecharge'),
      value: Number((acc?.totalRecharged || 0).toFixed(2)),
      unit: t('carbonCoin.unit'),
      delta: t('carbonCoin.statRechargeHint'),
      tone: 'pos' as const,
      icon: TrendCharts,
    },
    {
      label: t('carbonCoin.totalConsume'),
      value: Number((acc?.totalSpent || 0).toFixed(2)),
      unit: t('carbonCoin.unit'),
      delta: t('carbonCoin.statConsumeHint'),
      tone: 'neu' as const,
      icon: ShoppingCart,
    },
  ]
})
</script>

<template>
  <section class="carbon-coin-page pen-enter">
    <PageHeader
      :title="t('carbonCoin.title')"
      :description="t('carbonCoin.description')"
      :crumbs="[t('menu.accountManage'), t('menu.carbonCoinAccount')]"
    />

    <PageHint :text="t('pageHints.carbonCoin')" />

    <div class="account-info" data-testid="carbon-coin-status-card">
      <p class="info-label">{{ t('carbonCoin.accountInfo') }}<TermTip term="carbonCoin" /></p>
      <StatStrip :items="accountStats" :loading="accountLoading" />
    </div>

    <EmptyHint
      v-if="!accountData && !accountLoading"
      :description="t('nextSteps.emptyCoinTip')"
      :action-text="t('nextSteps.emptyCoinAction')"
      to="/enterprise/trading/market"
    />

    <DataPanel :title="t('carbonCoin.txRecord')" :meta="t('common.total', { count: total })">

      <el-table :data="transactionData" v-loading="transactionLoading">
        <el-table-column :label="t('common.colIndex')" width="80">
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="txNo" :label="t('carbonCoin.colTxNo')" min-width="180" />
        <el-table-column prop="txType" :label="t('carbonCoin.colTxType')" min-width="120">
          <template #default="{ row }">
            <el-tag :type="getTransactionTypeTag(row.txType)">
              {{ getTransactionTypeText(row.txType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" :label="t('carbonCoin.colAmount')" min-width="120">
          <template #default="{ row }">
            <span :class="getAmountClass(row.txType)">
              {{ formatAmount(row.txType, row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" :label="t('carbonCoin.colBalanceAfter')" min-width="120">
          <template #default="{ row }">{{ formatNumber(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('carbonCoin.colRemark')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('carbonCoin.colTime')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
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

  </section>
</template>

<style scoped>
.carbon-coin-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.account-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.info-label {
  display: inline-flex;
  align-items: center;
  margin: 0;
  font-size: var(--fs-caption);
  font-weight: 500;
  line-height: 1.4;
  color: var(--pen-faint);
}

.amount-positive {
  color: var(--pen-success);
  font-weight: 600;
}

.amount-negative {
  color: var(--pen-danger);
  font-weight: 600;
}
</style>
