<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 碳币资产代币钱包与转账清算 (Carbon Coin Account)
 * 【路由地址】：/enterprise/carbon-coin/account
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 碳币钱包资产卡片：展示可用余额、冻结金额与账户累计流动性
 *   2. 定向转账操作对话框：选择收款企业ID、录入转账金额与备注并确认
 *   3. 资金交易流水明细表：分页展示充值、扣减、交易结算与转账收支明细
 *   4. 碳币经济模型白话释义（PageHint / TermTip）
 * 【对应后端 Controller & API】：
 *   - CarbonCoinController:
 *     * GET  /api/v1/carbon-coin/account      -> getMyAccount() 获取钱包资产与余额
 *     * GET  /api/v1/carbon-coin/transactions -> getTransactions() 查询资金交易流水
 *     * POST /api/v1/carbon-coin/transfer     -> transferCoins() 执行企业间转账
 * ==============================================================================
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getMyAccount, getTransactions, transferCoins } from '../../api/carbonCoin'
import PageContainer from '../../components/PageContainer.vue'
import TermTip from '../../components/TermTip.vue'
import PageHint from '../../components/PageHint.vue'
import { formatDateTime, formatNumber } from '../../utils/format'
import type { CarbonCoinAccountResponse, CarbonCoinTransaction } from '../../types'
import EmptyHint from '../../components/EmptyHint.vue'

const { t } = useI18n()

const accountData = ref<CarbonCoinAccountResponse | null>(null)
const accountLoading = ref(false)

const transactionData = ref<CarbonCoinTransaction[]>([])
const transactionLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// Transfer dialog
const transferDialogVisible = ref(false)
const transferFormRef = ref()
const transferLoading = ref(false)

const transferForm = reactive({
  counterpartId: null as number | null,
  amount: null as number | null,
  remark: '',
})

const transferRules = {
  counterpartId: [{ required: true, message: t('carbonCoin.selectCounterpart'), trigger: 'change' }],
  amount: [{ required: true, message: t('carbonCoin.enterTransferAmount'), trigger: 'blur' }],
}

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

const openTransferDialog = () => {
  transferForm.counterpartId = null
  transferForm.amount = null
  transferForm.remark = ''
  transferDialogVisible.value = true
}

const handleTransfer = async () => {
  const valid = await transferFormRef.value?.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning(t('carbonCoin.checkForm'))
    return
  }

  if (!transferForm.amount || transferForm.amount <= 0) {
    ElMessage.error(t('carbonCoin.invalidAmount'))
    return
  }

  if (accountData.value && transferForm.amount > accountData.value.balance) {
    ElMessage.error(t('carbonCoin.insufficientBalance'))
    return
  }

  try {
    transferLoading.value = true
    await transferCoins({
      counterpartId: transferForm.counterpartId!,
      amount: transferForm.amount,
      remark: transferForm.remark || undefined,
    })
    ElMessage.success(t('carbonCoin.transferSuccess'))
    transferDialogVisible.value = false
    await loadAccount()
    await loadTransactions()
  } catch (error) {
    ElMessage.error(t('carbonCoin.transferFailed'))
  } finally {
    transferLoading.value = false
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
    2: 'success',
    3: 'danger',
    4: 'danger',
    5: 'warning',
    6: 'primary',
    7: 'danger',
    8: 'success',
  }
  return map[type] || 'info'
}

const getTransactionTypeText = (type: number) => {
  const map: Record<number, string> = {
    1: t('carbonCoin.txTypeRecharge'),
    2: t('carbonCoin.txTypeTransferIn'),
    3: t('carbonCoin.txTypeTransferOut'),
    4: t('carbonCoin.txTypeTransferOut'),
    5: t('carbonCoin.txTypeBuy'),
    6: t('carbonCoin.txTypeSell'),
    7: t('carbonCoin.txTypeFreeze'),
    8: t('carbonCoin.txTypeUnfreeze'),
  }
  return map[type] || t('common.status')
}

const getAccountStatusType = (status?: number) => (status === 1 ? 'success' : 'warning')

const getAccountStatusText = (status?: number) => (
  status === 1 ? t('carbonCoin.statusActive') : t('carbonCoin.statusInactive')
)


const getAmountClass = (type: number) => {
  const positiveTypes = [1, 2, 6]
  return positiveTypes.includes(type) ? 'amount-positive' : 'amount-negative'
}

const formatAmount = (type: number, amount: number) => {
  const positiveTypes = [1, 2, 6]
  const prefix = positiveTypes.includes(type) ? '+' : ''
  return prefix + amount
}

onMounted(() => {
  loadAccount()
  loadTransactions()
})
</script>

<template>
  <PageContainer :title="t('carbonCoin.title')" :description="t('carbonCoin.description')">
    <PageHint :text="t('pageHints.carbonCoin')" />
    <section class="carbon-coin-page">
      <el-card class="section-card" shadow="never" v-loading="accountLoading">
        <template #header>
          <span class="card-header">{{ t('carbonCoin.accountInfo') }}</span>
        </template>
        <div v-if="accountData" class="account-info">
          <div class="info-card main-balance">
            <div class="info-label">{{ t('carbonCoin.balance') }}<TermTip term="carbonCoin" /></div>
            <div class="info-value">{{ formatNumber(accountData.balance || 0) }}</div>
            <div class="info-unit">{{ t('carbonCoin.unit') }}</div>
          </div>
          <div class="info-card" data-testid="carbon-coin-status-card">
            <div class="info-label">{{ t('carbonCoin.accountStatus') }}</div>
            <div class="info-status">
              <el-tag :type="getAccountStatusType(accountData.status)" size="large">
                {{ getAccountStatusText(accountData.status) }}
              </el-tag>
            </div>
          </div>
          <div class="info-card">
            <div class="info-label">{{ t('carbonCoin.totalRecharge') }}</div>
            <div class="info-value">{{ formatNumber(accountData.totalRecharged || 0) }}</div>
            <div class="info-unit">{{ t('carbonCoin.unit') }}</div>
          </div>
          <div class="info-card">
            <div class="info-label">{{ t('carbonCoin.totalConsume') }}</div>
            <div class="info-value spent">{{ formatNumber(accountData.totalSpent || 0) }}</div>
            <div class="info-unit">{{ t('carbonCoin.unit') }}</div>
          </div>
        </div>
        <EmptyHint
          v-else
          :description="t('nextSteps.emptyCoinTip')"
          :action-text="t('nextSteps.emptyCoinAction')"
          to="/enterprise/trading/market"
        />
        <div class="transfer-action">
          <el-button type="primary" @click="openTransferDialog">{{ t('carbonCoin.transferBtn') }}</el-button>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>
          <span class="card-header">{{ t('carbonCoin.txRecord') }}</span>
        </template>
        <el-table :data="transactionData" border v-loading="transactionLoading">
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

    <!-- Transfer Dialog -->
    <el-dialog
      v-model="transferDialogVisible"
      :title="t('carbonCoin.transferDialog')"
      width="500px"
      destroy-on-close
    >
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="100px" scroll-to-error>
        <el-form-item :label="t('carbonCoin.counterpart')" prop="counterpartId">
          <el-input-number
            v-model="transferForm.counterpartId"
            :min="1"
            :precision="0"
            :placeholder="t('carbonCoin.selectCounterpart')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('carbonCoin.transferAmount')" prop="amount">
          <el-input-number
            v-model="transferForm.amount"
            :min="0.01"
            :precision="2"
            :placeholder="t('carbonCoin.enterTransferAmount')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('carbonCoin.transferRemark')">
          <el-input
            v-model="transferForm.remark"
            :placeholder="t('carbonCoin.enterRemark')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="transferLoading" @click="handleTransfer">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.carbon-coin-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.card-header {
  font-weight: 600;
  font-size: var(--text-md);
}

.account-info {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  padding: 10px 0;
}

.info-card {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  transition: transform 0.2s;
}

.info-card:hover {
  transform: translateY(-2px);
}

.info-card.main-balance {
  background: var(--color-accent);
  color: white;
}

.info-label {
  font-size: var(--text-base);
  margin-bottom: 12px;
  opacity: 0.9;
}

.info-value {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 8px;
  line-height: 1;
}

.info-unit {
  font-size: var(--text-xs);
  opacity: 0.8;
}

.info-value.spent {
  color: var(--el-color-warning);
}

.info-status {
  margin-top: 18px;
  display: flex;
  justify-content: center;
}

.amount-positive {
  color: var(--el-color-success);
  font-weight: 600;
}

.amount-negative {
  color: var(--el-color-danger);
  font-weight: 600;
}

.pagination-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.transfer-action {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
