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
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Document, Files, Tickets } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { getLatestBlocks, getStatus, getTransactions, queryTransaction } from '../../api/blockchain'
import { formatDateTime } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import FilterBar from '../../components/pen/FilterBar.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import PageHint from '../../components/PageHint.vue'
import TermTip from '../../components/TermTip.vue'
import { DATA_CENTER_ART } from '../../config/penImagery'
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

/** 首屏统计条：全部由已加载的本页数据推导，不新增接口请求 */
const overviewStats = computed(() => {
  const connected = Boolean(chainStatus.value?.connected)
  const channel = chainStatus.value?.channel
  const mode = chainStatus.value?.mode

  return [
    {
      label: t('blockchain.statusTitle'),
      value: formatStatusValue(chainStatus.value?.connected),
      unit: '',
      delta: channel ? `${t('blockchain.statusChannel')}: ${channel}` : t('blockchain.statChannelHint'),
      tone: connected ? ('pos' as const) : ('neg' as const),
      icon: Connection,
    },
    {
      label: t('blockchain.statusPeers'),
      value: formatStatusValue(chainStatus.value?.peers),
      unit: t('blockchain.statUnitNodes'),
      delta: mode ? `${t('blockchain.statusMode')}: ${mode}` : t('blockchain.statChannelHint'),
      tone: 'neu' as const,
      icon: Files,
    },
    {
      label: t('blockchain.tabBlocks'),
      value: blocksTotal.value,
      unit: t('blockchain.statUnitBlocks'),
      delta: t('blockchain.statBlocksHint'),
      tone: 'neu' as const,
      icon: Document,
    },
    {
      label: t('blockchain.tabTransactions'),
      value: transactionsTotal.value,
      unit: t('blockchain.statUnitTx'),
      delta: t('blockchain.statTxHint'),
      tone: transactionsTotal.value > 0 ? ('pos' as const) : ('neu' as const),
      icon: Tickets,
    },
  ]
})

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
  <section class="blockchain-page pen-enter">
    <PageHeader
      :title="t('blockchain.title')"
      :description="t('blockchain.description')"
      :crumbs="[t('blockchain.breadcrumbChain'), t('blockchain.breadcrumbExplorer')]"
    />

    <PageHint :text="t('pageHints.blockchain')" />

    <StatStrip :items="overviewStats" :loading="statusLoading || blocksLoading || transactionsLoading" />

    <FilterBar :aria-label="t('blockchain.queryTitle')">
      <label class="pen-field query-field">
        <span class="pen-field__label">{{ t('blockchain.colTxHash') }}</span>
        <el-input
          v-model="txHashQuery"
          data-testid="blockchain-tx-query-input"
          :placeholder="t('blockchain.queryPlaceholder')"
          clearable
          @keyup.enter="submitTxQuery"
        />
      </label>
      <el-button
        type="primary"
        :loading="txQueryLoading"
        data-testid="blockchain-tx-query-submit"
        @click="submitTxQuery"
      >
        {{ t('blockchain.queryAction') }}
      </el-button>
    </FilterBar>

    <!-- 网络拓扑状态 + 哈希溯源结果：合并为单一面板 -->
    <DataPanel :title="t('blockchain.panelStatusQueryTitle')" :meta="t('blockchain.panelStatusQueryMeta')">
      <template #actions>
        <el-tag
          class="status-tag"
          :type="statusTagType(chainStatus?.connected)"
          data-testid="blockchain-status-tag"
        >
          {{ formatStatusValue(chainStatus?.connected) }}
        </el-tag>
      </template>

      <div class="panel-pad">
        <div class="status-query-grid">
          <div class="status-panel">
            <div class="sub-head">
              <span class="sub-head__title">{{ t('blockchain.statusTitle') }}</span>
              <span class="sub-head__desc">{{ t('blockchain.statusDescription') }}</span>
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
            <div class="sub-head">
              <span class="sub-head__title">{{ t('blockchain.queryTitle') }}</span>
              <span class="sub-head__desc">{{ t('blockchain.queryDescription') }}</span>
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
                <strong class="query-hash">{{ formatStatusValue(txQueryResult.txHash) }}</strong>
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

            <p v-else-if="!txQueryError" class="query-empty">{{ t('blockchain.queryEmpty') }}</p>
          </div>
        </div>
      </div>
    </DataPanel>

    <el-tabs v-model="activeTab" class="blockchain-tabs">
      <el-tab-pane :label="t('blockchain.tabBlocks')" name="blocks">
        <DataPanel
          :title="t('blockchain.tabBlocks')"
          :meta="t('common.total', { count: blocksTotal })"
        >
          <el-table :data="blocks" v-loading="blocksLoading" :empty-text="blocksLoading ? t('common.loading') : t('common.noData')">
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

          <template #footer>
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
          </template>
        </DataPanel>
      </el-tab-pane>

      <el-tab-pane :label="t('blockchain.tabTransactions')" name="transactions">
        <DataPanel
          :title="t('blockchain.tabTransactions')"
          :meta="t('common.total', { count: transactionsTotal })"
        >
          <el-table :data="transactions" v-loading="transactionsLoading" :empty-text="transactionsLoading ? t('common.loading') : t('common.noData')">
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

          <template #footer>
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
          </template>
        </DataPanel>
      </el-tab-pane>
    </el-tabs>

    <!-- pen 稿配图：联盟链的物理载体（真实机房设备，非发光特效） -->
    <figure class="page-art">
      <img :src="DATA_CENTER_ART" :alt="t('blockchain.pageArtAlt')" loading="lazy" decoding="async" />
    </figure>
  </section>
</template>

<style scoped>
.blockchain-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.panel-pad {
  padding: var(--space-4);
}

.query-field {
  min-width: 360px;
  flex: 1 1 360px;
}

.status-query-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(280px, 1fr));
  gap: var(--space-5);
}

.status-panel,
.query-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
}

.sub-head {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.sub-head__title {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--pen-ink);
}

.sub-head__desc {
  font-size: var(--fs-caption);
  color: var(--pen-faint);
  line-height: 1.5;
}

.status-tag {
  flex-shrink: 0;
}

.status-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: var(--space-3);
}

.status-metric,
.query-result-row {
  display: flex;
  flex-direction: column;
  gap: var(--space-1-5);
  padding: var(--space-3);
  border-radius: var(--radius-card);
  background: var(--pen-raised);
}

.status-label {
  color: var(--pen-faint);
  font-size: var(--fs-caption);
}

.query-alert {
  margin-top: 0;
}

.query-empty {
  margin: 0;
  padding: var(--space-3);
  border-radius: var(--radius-card);
  background: var(--pen-raised);
  color: var(--pen-faint);
  font-size: var(--fs-sub);
}

.query-result {
  display: grid;
  gap: var(--space-2-5);
}

.blockchain-explainer-card {
  padding: var(--space-2-5) var(--space-3);
  background: var(--pen-note-bg);
  border: 1px solid var(--pen-note-bd);
  border-radius: var(--radius-card);
  line-height: 1.6;
}

.explainer-title {
  display: flex;
  align-items: center;
  gap: var(--space-1-5);
  font-weight: 500;
  font-size: var(--fs-sub);
  color: var(--pen-ink);
  margin-bottom: var(--space-1);
}

.explainer-content {
  font-size: var(--fs-caption);
  color: var(--pen-muted);
}

.query-hash {
  word-break: break-all;
  font-size: var(--fs-sub);
}

.blockchain-tabs {
  padding: 0;
}

/* pen 稿配图：通栏、150px 高、cover 填充、2px 圆角 */
.page-art {
  margin: 0;
  width: 100%;
  height: 150px;
  border: 1px solid var(--pen-border);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.page-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

@media (max-width: 900px) {
  .status-query-grid {
    grid-template-columns: 1fr;
  }

  .status-metrics {
    grid-template-columns: 1fr 1fr;
  }

  .query-field {
    min-width: 0;
    flex-basis: 100%;
  }
}
</style>
