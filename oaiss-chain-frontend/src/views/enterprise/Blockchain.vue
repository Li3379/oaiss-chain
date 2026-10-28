<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：企业端 - 超级账本（Hyperledger Fabric）区块链浏览器 (Blockchain Explorer)
 * 【路由地址】：/enterprise/blockchain/browser
 * 【访问权限】：ROLE_ENTERPRISE (企业用户)
 * 【界面可见功能】：
 *   1. 底层 Fabric 网络拓扑状态卡片：通道名称、组织节点数、背书策略、链码版本
 *   2. 链上交易哈希 (TxHash) 穿透溯源检索栏：输入 64 位 TxID 即时查询存证原件
 *   3. 最新区块高度瀑布流列表：展示区块号、前序哈希、数据哈希、交易数量与出块时间
 *   4. 链上存证交易流列表：展示交易哈希、发起企业组织、交易动作类型与时间戳
 *   5. 区块链不可篡改特性科普释义（PageHint / TermTip）
 * 【对应后端 Controller & API】：
 *   - BlockchainController:
 *     * GET /api/v1/blockchain/status             -> getStatus() 获取底层网络健康状态
 *     * GET /api/v1/blockchain/blocks/latest      -> getLatestBlocks() 分页查询最新区块
 *     * GET /api/v1/blockchain/transactions       -> getTransactions() 分页查询交易存证
 *     * GET /api/v1/blockchain/transaction/{hash} -> queryTransaction() 按TxID穿透查询
 * ==============================================================================
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { getLatestBlocks, getStatus, getTransactions, queryTransaction } from '../../api/blockchain'
import { formatDateTime } from '../../utils/format'
import PageContainer from '../../components/PageContainer.vue'
import PageHint from '../../components/PageHint.vue'
import TermTip from '../../components/TermTip.vue'
import type { BlockchainBlockResponse, BlockchainStatusResponse, BlockchainTransactionResponse } from '../../types'

const { t } = useI18n()

const activeTab = ref('blocks')

const chainStatus = ref<BlockchainStatusResponse | null>(null)
const statusLoading = ref(false)
const txHashQuery = ref('')
const txQueryLoading = ref(false)
const txQueryResult = ref<BlockchainTransactionResponse | Record<string, unknown> | null>(null)
const txQueryError = ref('')

const blocks = ref<BlockchainBlockResponse[]>([])
const blocksLoading = ref(false)
const blocksCurrentPage = ref(1)
const blocksPageSize = ref(10)
const blocksTotal = ref(0)

const transactions = ref<BlockchainTransactionResponse[]>([])
const transactionsLoading = ref(false)
const transactionsCurrentPage = ref(1)
const transactionsPageSize = ref(10)
const transactionsTotal = ref(0)

const parseRecord = <T>(value: unknown): T | Record<string, unknown> | null => {
  if (!value) return null
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) as T
    } catch {
      return { raw: value }
    }
  }
  if (typeof value === 'object') {
    return value as T
  }
  return { value }
}

const formatStatusValue = (value: unknown) => {
  if (typeof value === 'boolean') {
    return value ? t('blockchain.statusOnline') : t('blockchain.statusOffline')
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

const statusTagType = (connected: unknown) => {
  return connected ? 'success' : 'danger'
}

const loadStatus = async () => {
  try {
    statusLoading.value = true
    const result = parseRecord<BlockchainStatusResponse>(await getStatus())
    chainStatus.value = result && 'connected' in result
      ? {
          connected: Boolean(result.connected),
          channel: typeof result.channel === 'string' ? result.channel : undefined,
          peers: typeof result.peers === 'number' ? result.peers : undefined,
          orderers: typeof result.orderers === 'number' ? result.orderers : undefined,
          mode: typeof result.mode === 'string' ? result.mode : undefined,
          mspId: typeof result.mspId === 'string' ? result.mspId : undefined,
          chaincode: typeof result.chaincode === 'string' ? result.chaincode : undefined,
          caEnabled: typeof result.caEnabled === 'boolean' ? result.caEnabled : undefined,
          timestamp: typeof result.timestamp === 'string' ? result.timestamp : undefined,
        }
      : null
  } catch {
    chainStatus.value = null
    ElMessage.error(t('blockchain.loadStatusFailed'))
  } finally {
    statusLoading.value = false
  }
}

const loadBlocks = async () => {
  try {
    blocksLoading.value = true
    const result = await getLatestBlocks({
      pageNum: blocksCurrentPage.value,
      pageSize: blocksPageSize.value,
    })
    blocks.value = result?.items || []
    blocksTotal.value = result?.total || 0
  } catch {
    ElMessage.error(t('blockchain.loadBlocksFailed'))
  } finally {
    blocksLoading.value = false
  }
}

const loadTransactions = async () => {
  try {
    transactionsLoading.value = true
    const result = await getTransactions({
      pageNum: transactionsCurrentPage.value,
      pageSize: transactionsPageSize.value,
    })
    transactions.value = result?.items || []
    transactionsTotal.value = result?.total || 0
  } catch {
    ElMessage.error(t('blockchain.loadTxFailed'))
  } finally {
    transactionsLoading.value = false
  }
}

const onBlocksSizeChange = (size: number) => {
  blocksPageSize.value = size
  blocksCurrentPage.value = 1
  loadBlocks()
}

const onBlocksCurrentChange = (page: number) => {
  blocksCurrentPage.value = page
  loadBlocks()
}

const onTransactionsSizeChange = (size: number) => {
  transactionsPageSize.value = size
  transactionsCurrentPage.value = 1
  loadTransactions()
}

const onTransactionsCurrentChange = (page: number) => {
  transactionsCurrentPage.value = page
  loadTransactions()
}

const getBlockTypeTag = (type?: string) => {
  const map: Record<string, string> = {
    'GENESIS': 'danger',
    'REGULAR': 'primary',
    'REWARD': 'success',
  }
  return type ? (map[type] || 'info') : 'info'
}

const getBlockTypeText = (type?: string) => {
  const map: Record<string, string> = {
    'GENESIS': t('blockchain.blockTypeGenesis'),
    'REGULAR': t('blockchain.blockTypeRegular'),
    'REWARD': t('blockchain.blockTypeReward'),
  }
  return type ? (map[type] || type) : type
}

const getTransactionStatusTag = (status?: string) => {
  const map: Record<string, string> = {
    'PENDING': 'warning',
    'CONFIRMED': 'success',
    'VALID': 'success',
    'FAILED': 'danger',
  }
  return status ? (map[status] || 'info') : 'info'
}

const getTransactionStatusText = (status?: string) => {
  const map: Record<string, string> = {
    'PENDING': t('blockchain.txStatusPending'),
    'CONFIRMED': t('blockchain.txStatusConfirmed'),
    'VALID': t('blockchain.txStatusValidated'),
    'FAILED': t('blockchain.txStatusFailed'),
  }
  return status ? (map[status] || status) : status
}

const submitTxQuery = async () => {
  if (!txHashQuery.value.trim()) {
    txQueryError.value = t('blockchain.queryTxRequired')
    txQueryResult.value = null
    return
  }

  try {
    txQueryLoading.value = true
    txQueryError.value = ''
    const result = parseRecord<BlockchainTransactionResponse>(await queryTransaction(txHashQuery.value.trim()))
    txQueryResult.value = result
  } catch (error) {
    txQueryResult.value = null
    txQueryError.value = error instanceof Error ? error.message : t('blockchain.queryTxFailed')
  } finally {
    txQueryLoading.value = false
  }
}

onMounted(() => {
  loadStatus()
  loadBlocks()
  loadTransactions()
})
</script>

<template>
  <PageContainer :title="t('blockchain.title')" :description="t('blockchain.description')">
    <PageHint :text="t('pageHints.blockchain')" />
    <section class="blockchain-page">
      <el-card class="section-card" shadow="never">
        <div class="status-query-grid">
          <div class="status-panel">
            <div class="panel-header">
              <div>
                <h3 class="panel-title">{{ t('blockchain.statusTitle') }}</h3>
                <p class="panel-subtitle">{{ t('blockchain.statusDescription') }}</p>
              </div>
              <el-tag
                class="status-tag"
                :type="statusTagType(chainStatus?.connected)"
                data-testid="blockchain-status-tag"
              >
                {{ formatStatusValue(chainStatus?.connected) }}
              </el-tag>
            </div>

            <div v-loading="statusLoading" class="status-metrics">
              <div class="status-metric">
                <span class="status-label">{{ t('blockchain.statusMode') }}</span>
                <strong>{{ formatStatusValue(chainStatus?.mode) }}</strong>
              </div>
              <div class="status-metric">
                <span class="status-label">{{ t('blockchain.statusChannel') }}</span>
                <strong>{{ formatStatusValue(chainStatus?.channel) }}</strong>
              </div>
              <div class="status-metric">
                <span class="status-label">{{ t('blockchain.statusPeers') }}</span>
                <strong>{{ formatStatusValue(chainStatus?.peers) }}</strong>
              </div>
              <div class="status-metric">
                <span class="status-label">{{ t('blockchain.statusOrderers') }}</span>
                <strong>{{ formatStatusValue(chainStatus?.orderers) }}</strong>
              </div>
            </div>
          </div>

          <div class="query-panel">
            <div class="panel-header">
              <div>
                <h3 class="panel-title">{{ t('blockchain.queryTitle') }}</h3>
                <p class="panel-subtitle">{{ t('blockchain.queryDescription') }}</p>
              </div>
            </div>

            <div class="query-form">
              <el-input
                v-model="txHashQuery"
                data-testid="blockchain-tx-query-input"
                :placeholder="t('blockchain.queryPlaceholder')"
                clearable
                @keyup.enter="submitTxQuery"
              />
              <el-button
                type="primary"
                :loading="txQueryLoading"
                data-testid="blockchain-tx-query-submit"
                @click="submitTxQuery"
              >
                {{ t('blockchain.queryAction') }}
              </el-button>
            </div>

            <el-alert
              v-if="txQueryError"
              class="query-alert"
              type="error"
              :closable="false"
              :title="txQueryError"
            />

            <div
              v-if="txQueryResult"
              class="query-result"
              data-testid="blockchain-tx-query-result"
            >
              <div class="blockchain-explainer-card">
                <div class="explainer-title"><el-icon><Document /></el-icon> {{ t('blockchainExplainer.title') }}</div>
                <div class="explainer-content">{{ t('blockchainExplainer.body') }}</div>
              </div>

              <div class="query-result-row">
                <span class="status-label">{{ t('blockchain.colTxHash') }}</span>
                <strong>{{ formatStatusValue(txQueryResult.txHash) }}</strong>
              </div>
              <div class="query-result-row">
                <span class="status-label">{{ t('blockchain.colTxStatus') }}</span>
                <el-tag :type="getTransactionStatusTag(String(txQueryResult.status || ''))">
                  {{ getTransactionStatusText(String(txQueryResult.status || '-')) }}
                </el-tag>
              </div>
              <div class="query-result-row">
                <span class="status-label">{{ t('blockchain.colTimestamp') }}</span>
                <strong>{{ formatDateTime(typeof txQueryResult.timestamp === 'string' ? txQueryResult.timestamp : '') || formatStatusValue(txQueryResult.timestamp) }}</strong>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <el-tabs v-model="activeTab" class="blockchain-tabs">
          <el-tab-pane :label="t('blockchain.tabBlocks')" name="blocks">
            <el-table :data="blocks" border v-loading="blocksLoading" :empty-text="blocksLoading ? t('common.loading') : t('common.noData')">
              <el-table-column :label="t('common.operation')" width="80">
                <template #default="scope">
                  {{ (blocksCurrentPage - 1) * blocksPageSize + scope.$index + 1 }}
                </template>
              </el-table-column>
              <el-table-column prop="blockNumber" :label="t('blockchain.colBlockHeight')" min-width="120">
                <template #header>
                  <span>{{ t('blockchain.colBlockHeight') }}<TermTip term="blockchainHash" /></span>
                </template>
              </el-table-column>
              <el-table-column prop="blockHash" :label="t('blockchain.colBlockHash')" min-width="200" show-overflow-tooltip />
              <el-table-column prop="blockType" :label="t('blockchain.colBlockType')" min-width="100">
                <template #default="{ row }">
                  <el-tag :type="getBlockTypeTag(row.blockType)">
                    {{ getBlockTypeText(row.blockType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="txCount" :label="t('blockchain.colTxCount')" min-width="100" />
              <el-table-column prop="miner" :label="t('blockchain.colMinerAddress')" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.miner || '-' }}</template>
              </el-table-column>
              <el-table-column prop="timestamp" :label="t('blockchain.colTimestamp')" min-width="170">
                <template #default="{ row }">{{ formatDateTime(row.timestamp) }}</template>
              </el-table-column>
            </el-table>

            <div class="pagination-row">
              <el-pagination
                v-model:current-page="blocksCurrentPage"
                v-model:page-size="blocksPageSize"
                background
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="blocksTotal"
                @size-change="onBlocksSizeChange"
                @current-change="onBlocksCurrentChange"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane :label="t('blockchain.tabTransactions')" name="transactions">
            <el-table :data="transactions" border v-loading="transactionsLoading" :empty-text="transactionsLoading ? t('common.loading') : t('common.noData')">
              <el-table-column :label="t('common.operation')" width="80">
                <template #default="scope">
                  {{ (transactionsCurrentPage - 1) * transactionsPageSize + scope.$index + 1 }}
                </template>
              </el-table-column>
              <el-table-column prop="txHash" :label="t('blockchain.colTxHash')" min-width="200" show-overflow-tooltip>
                <template #header>
                  <span>{{ t('blockchain.colTxHash') }}<TermTip term="blockchainHash" /></span>
                </template>
              </el-table-column>
              <el-table-column prop="blockNumber" :label="t('blockchain.colBlockHeight')" min-width="120" />
              <el-table-column prop="fromAddress" :label="t('blockchain.colSender')" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.fromAddress || '-' }}</template>
              </el-table-column>
              <el-table-column prop="toAddress" :label="t('blockchain.colReceiver')" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.toAddress || '-' }}</template>
              </el-table-column>
              <el-table-column prop="amount" :label="t('blockchain.colAmount')" min-width="120">
                <template #default="{ row }">{{ row.amount ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="status" :label="t('blockchain.colTxStatus')" min-width="100">
                <template #default="{ row }">
                  <el-tag :type="getTransactionStatusTag(row.status)">
                    {{ getTransactionStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="timestamp" :label="t('blockchain.colTimestamp')" min-width="170">
                <template #default="{ row }">{{ formatDateTime(row.timestamp) }}</template>
              </el-table-column>
            </el-table>

            <div class="pagination-row">
              <el-pagination
                v-model:current-page="transactionsCurrentPage"
                v-model:page-size="transactionsPageSize"
                background
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="transactionsTotal"
                @size-change="onTransactionsSizeChange"
                @current-change="onTransactionsCurrentChange"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </section>
  </PageContainer>
</template>

<style scoped>
.blockchain-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.status-query-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(280px, 1fr));
  gap: 18px;
}

.section-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.status-panel,
.query-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 600;
}

.panel-subtitle {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
  line-height: 1.5;
}

.status-tag {
  flex-shrink: 0;
}

.status-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 12px;
}

.status-metric,
.query-result-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.status-label {
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
}

.query-form {
  display: flex;
  gap: 10px;
}

.query-alert {
  margin-top: 4px;
}

.query-result {
  display: grid;
  gap: 10px;
}

.blockchain-explainer-card {
  padding: 10px 14px;
  background: rgba(20, 167, 154, 0.08);
  border: 1px solid rgba(20, 167, 154, 0.25);
  border-radius: 8px;
  line-height: 1.6;
}

.explainer-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: var(--text-sm);
  color: var(--el-color-primary);
  margin-bottom: 4px;
}

.explainer-content {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.blockchain-tabs {
  padding: 0 10px;
}

.pagination-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .status-query-grid {
    grid-template-columns: 1fr;
  }

  .status-metrics {
    grid-template-columns: 1fr 1fr;
  }

  .query-form {
    flex-direction: column;
  }
}
</style>
