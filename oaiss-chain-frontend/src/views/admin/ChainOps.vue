<script setup lang="ts">
/**
 * ==============================================================================
 * 【页面模块】：管理员端 - 链上数据管理（对账 + 死信 + 链上浏览）(Chain Ops)
 * 【路由地址】：/admin/blockchain
 * 【访问权限】：ROLE_ADMIN (系统管理员)
 * 【界面可见功能】：
 *   1. 链上对账：把「本地认为已上链」的报告逐条与链上快照比对，输出四类结果
 *   2. 死信队列：超过重试上限仍未上链的消息，可单条重放
 *   3. 链上浏览：区块流与交易流（复用企业端同一套只读数据）
 *   4. 机制释义（PageHint）：为什么链上数据「不能改，只能对账与补偿」
 * 【对应后端 Controller & API】：
 *   - AdminController:
 *     * GET  /api/v1/admin/blockchain/reconciliation -> getChainReconciliation()
 *     * GET  /api/v1/admin/outbox/dead-letters       -> getDeadLetters()
 *     * POST /api/v1/admin/outbox/{id}/replay        -> replayDeadLetter()
 *   - BlockchainController:
 *     * GET  /api/v1/blockchain/blocks/latest        -> getLatestBlocks()
 *     * GET  /api/v1/blockchain/transactions         -> getTransactions()
 * 【设计说明】：
 *   Fabric 账本是只读追加的，本页刻意不提供任何「删除 / 回滚 / 覆盖链上数据」的操作。
 *   「还原」在不可篡改账本上的真实含义只有两种：对账发现差异、把死信重新投递。
 *   给出一个能改链的按钮只会制造假象。
 * ==============================================================================
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Document, Refresh, Tickets } from '@element-plus/icons-vue'
import {
  getChainReconciliation,
  getDeadLetters,
  markHistoricalMockData,
  replayDeadLetter,
  type ChainReconciliationResult,
  type DeadLetterRow,
} from '../../api/admin'
import { getLatestBlocks, getStatus, getTransactions } from '../../api/blockchain'
import { formatDateTime, formatNumber } from '../../utils/format'
import PageHeader from '../../components/pen/PageHeader.vue'
import PageHint from '../../components/PageHint.vue'
import StatStrip from '../../components/pen/StatStrip.vue'
import DataPanel from '../../components/pen/DataPanel.vue'
import type { BlockchainBlockResponse, BlockchainStatusResponse, BlockchainTransactionResponse } from '../../types'

const { t } = useI18n()

const activeTab = ref('reconciliation')

// ---------------- 对账 ----------------
const reconLoading = ref(false)
const reconResult = ref<ChainReconciliationResult | null>(null)

const loadReconciliation = async () => {
  reconLoading.value = true
  try {
    reconResult.value = await getChainReconciliation({ pageNum: 1, pageSize: 20 })
  } catch {
    ElMessage.error(t('chainOps.reconciliationFailed'))
    reconResult.value = null
  } finally {
    reconLoading.value = false
  }
}

const reconDetails = computed(() => reconResult.value?.details || [])

/** 标记历史 Mock 脏数据：把「本地记了上链、链上从没写过」的老记录从缺失告警里剥离 */
const marking = ref(false)

const onMarkHistoricalMock = async () => {
  if (marking.value) return
  try {
    await ElMessageBox.confirm(
      t('chainOps.markMockConfirm'),
      t('chainOps.markMockTitle'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' },
    )
  } catch {
    return
  }
  marking.value = true
  try {
    const result = await markHistoricalMockData()
    ElMessage.success(t('chainOps.markMockSuccess', { count: result?.marked ?? 0 }))
    await loadReconciliation()
  } catch {
    ElMessage.error(t('chainOps.markMockFailed'))
  } finally {
    marking.value = false
  }
}

/** 明细行状态 → 标签色。只有 MATCHED 是正常的，其余都需要人看。 */
const reconStatusTag = (status?: string) => {
  switch (status) {
    case 'MATCHED': return 'success'
    case 'MISSING_ON_CHAIN': return 'danger'
    case 'CONTENT_MISMATCH': return 'warning'
    case 'UNVERIFIABLE': return 'info'
    case 'HISTORICAL_MOCK': return 'info'
    default: return 'info'
  }
}

const reconStatusText = (status?: string) => {
  switch (status) {
    case 'MATCHED': return t('chainOps.statusMatched')
    case 'MISSING_ON_CHAIN': return t('chainOps.statusMissingOnChain')
    case 'CONTENT_MISMATCH': return t('chainOps.statusContentMismatch')
    case 'UNVERIFIABLE': return t('chainOps.statusUnverifiable')
    case 'HISTORICAL_MOCK': return t('chainOps.statusHistoricalMock')
    default: return status || '-'
  }
}

// ---------------- 死信 ----------------
const deadLoading = ref(false)
const deadRows = ref<DeadLetterRow[]>([])
const deadTotal = ref(0)
const deadPage = ref(1)
const deadPageSize = ref(10)
const replaying = ref<number | null>(null)

const loadDeadLetters = async () => {
  deadLoading.value = true
  try {
    const page = await getDeadLetters({ pageNum: deadPage.value, pageSize: deadPageSize.value })
    deadRows.value = page.items || []
    deadTotal.value = page.total || 0
  } catch {
    ElMessage.error(t('chainOps.deadLoadFailed'))
    deadRows.value = []
    deadTotal.value = 0
  } finally {
    deadLoading.value = false
  }
}

const onReplay = async (row: DeadLetterRow) => {
  if (replaying.value !== null) return
  try {
    await ElMessageBox.confirm(
      t('chainOps.replayConfirm', { id: row.id }),
      t('chainOps.replayTitle'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' },
    )
  } catch {
    return
  }
  replaying.value = row.id
  try {
    await replayDeadLetter(row.id)
    ElMessage.success(t('chainOps.replaySuccess'))
    await loadDeadLetters()
  } catch {
    ElMessage.error(t('chainOps.replayFailed'))
  } finally {
    replaying.value = null
  }
}

// ---------------- 链上浏览 ----------------
const chainStatus = ref<BlockchainStatusResponse | null>(null)
const blocks = ref<BlockchainBlockResponse[]>([])
const blocksTotal = ref(0)
const transactions = ref<BlockchainTransactionResponse[]>([])
const transactionsTotal = ref(0)
const browseLoading = ref(false)

const loadChainBrowse = async () => {
  browseLoading.value = true
  try {
    const [status, blockPage, txPage] = await Promise.all([
      getStatus(),
      getLatestBlocks({ pageNum: 1, pageSize: 10 }),
      getTransactions({ pageNum: 1, pageSize: 10 }),
    ])
    chainStatus.value = status
    blocks.value = blockPage?.items || []
    blocksTotal.value = blockPage?.total || 0
    transactions.value = txPage?.items || []
    transactionsTotal.value = txPage?.total || 0
  } catch {
    ElMessage.error(t('blockchain.loadBlocksFailed'))
  } finally {
    browseLoading.value = false
  }
}

const overviewStats = computed(() => {
  const recon = reconResult.value
  return [
    {
      label: t('chainOps.statChecked'),
      value: recon?.checked ?? 0,
      unit: t('chainOps.unitReports'),
      delta: t('chainOps.statCheckedHint'),
      tone: 'neu' as const,
      icon: Document,
    },
    {
      label: t('chainOps.statusMissingOnChain'),
      value: recon?.missingOnChain ?? 0,
      unit: t('chainOps.unitReports'),
      delta: t('chainOps.statMissingHint'),
      tone: (recon?.missingOnChain ?? 0) > 0 ? ('neg' as const) : ('neu' as const),
      icon: Connection,
    },
    {
      label: t('chainOps.statusContentMismatch'),
      value: recon?.contentMismatch ?? 0,
      unit: t('chainOps.unitReports'),
      delta: t('chainOps.statMismatchHint'),
      tone: (recon?.contentMismatch ?? 0) > 0 ? ('neg' as const) : ('neu' as const),
      icon: Refresh,
    },
    {
      label: t('chainOps.statusHistoricalMock'),
      value: recon?.historicalMock ?? 0,
      unit: t('chainOps.unitReports'),
      delta: t('chainOps.statHistoricalMockHint'),
      tone: 'neu' as const,
      icon: Document,
    },
    {
      label: t('chainOps.tabDeadLetters'),
      value: deadTotal.value,
      unit: t('chainOps.unitMessages'),
      delta: t('chainOps.statDeadHint'),
      tone: deadTotal.value > 0 ? ('neg' as const) : ('neu' as const),
      icon: Tickets,
    },
  ]
})

onMounted(() => {
  loadReconciliation()
  loadDeadLetters()
  loadChainBrowse()
})
</script>

<template>
  <section class="chain-ops-page pen-enter">
    <PageHeader
      :title="t('chainOps.title')"
      :description="t('chainOps.description')"
      :crumbs="[t('chainOps.breadcrumbOps'), t('chainOps.breadcrumbChain')]"
    />

    <PageHint :text="t('pageHints.chainOps')" />

    <StatStrip :items="overviewStats" :loading="reconLoading || deadLoading || browseLoading" />

    <el-tabs v-model="activeTab" class="chain-ops-tabs">
      <el-tab-pane :label="t('chainOps.tabReconciliation')" name="reconciliation">
        <DataPanel :title="t('chainOps.reconciliationTitle')" :meta="t('chainOps.reconciliationMeta')">
          <template #actions>
            <el-button :loading="marking" @click="onMarkHistoricalMock">
              {{ t('chainOps.markHistoricalMock') }}
            </el-button>
            <el-button :icon="Refresh" :loading="reconLoading" @click="loadReconciliation">
              {{ t('chainOps.runReconciliation') }}
            </el-button>
          </template>

          <el-table
            :data="reconDetails"
            v-loading="reconLoading"
            :empty-text="reconLoading ? t('common.loading') : t('chainOps.reconciliationEmpty')"
          >
            <el-table-column prop="reportNo" :label="t('chainOps.colReportNo')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.reportNo || row.reportId }}</template>
            </el-table-column>
            <el-table-column prop="dbTotalEmission" :label="t('chainOps.colDbEmission')" min-width="140">
              <template #default="{ row }">{{ row.dbTotalEmission ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="chainTotalEmission" :label="t('chainOps.colChainEmission')" min-width="140">
              <template #default="{ row }">{{ row.chainTotalEmission ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('chainOps.colReconStatus')" min-width="150">
              <template #default="{ row }">
                <el-tag :type="reconStatusTag(row.status)">{{ reconStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="t('chainOps.colReason')" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.reason || '-' }}</template>
            </el-table-column>
          </el-table>
        </DataPanel>
      </el-tab-pane>

      <el-tab-pane :label="t('chainOps.tabDeadLetters')" name="dead">
        <DataPanel :title="t('chainOps.tabDeadLetters')" :meta="t('common.total', { count: deadTotal })">
          <template #actions>
            <el-button :icon="Refresh" :loading="deadLoading" @click="loadDeadLetters">
              {{ t('common.refresh') }}
            </el-button>
          </template>

          <el-table
            :data="deadRows"
            v-loading="deadLoading"
            :empty-text="deadLoading ? t('common.loading') : t('chainOps.deadEmpty')"
          >
            <el-table-column prop="messageId" :label="t('chainOps.colMessageId')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="aggregateType" :label="t('chainOps.colAggregate')" min-width="110">
              <template #default="{ row }">{{ row.aggregateType }} #{{ row.aggregateId }}</template>
            </el-table-column>
            <el-table-column prop="topic" :label="t('chainOps.colTopic')" min-width="170" show-overflow-tooltip />
            <el-table-column prop="retryCount" :label="t('chainOps.colRetryCount')" min-width="90" />
            <el-table-column prop="errorMessage" :label="t('chainOps.colError')" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" :label="t('chainOps.colCreatedAt')" min-width="165">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  :loading="replaying === row.id"
                  @click="onReplay(row)"
                >
                  {{ t('chainOps.replay') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <template #footer>
            <el-pagination
              v-model:current-page="deadPage"
              v-model:page-size="deadPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="deadTotal"
              @size-change="loadDeadLetters"
              @current-change="loadDeadLetters"
            />
          </template>
        </DataPanel>
      </el-tab-pane>

      <el-tab-pane :label="t('chainOps.tabBrowse')" name="browse">
        <div class="browse-grid">
          <DataPanel :title="t('blockchain.tabBlocks')" :meta="t('common.total', { count: blocksTotal })">
            <el-table :data="blocks" v-loading="browseLoading" :empty-text="t('common.noData')">
              <el-table-column prop="blockNumber" :label="t('blockchain.colBlockHeight')" min-width="110" />
              <el-table-column prop="blockHash" :label="t('blockchain.colBlockHash')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="txCount" :label="t('blockchain.colTxCount')" min-width="90" />
              <el-table-column prop="timestamp" :label="t('blockchain.colTimestamp')" min-width="165">
                <template #default="{ row }">{{ formatDateTime(row.timestamp) }}</template>
              </el-table-column>
            </el-table>
          </DataPanel>

          <DataPanel :title="t('blockchain.tabTransactions')" :meta="t('common.total', { count: transactionsTotal })">
            <el-table :data="transactions" v-loading="browseLoading" :empty-text="t('common.noData')">
              <el-table-column prop="txHash" :label="t('blockchain.colTxHash')" min-width="190" show-overflow-tooltip />
              <el-table-column prop="blockNumber" :label="t('blockchain.colBlockHeight')" min-width="110" />
              <el-table-column prop="senderMsp" :label="t('blockchain.colSenderMsp')" min-width="130">
                <template #default="{ row }">{{ row.senderMsp || '-' }}</template>
              </el-table-column>
              <el-table-column prop="function" :label="t('blockchain.colFunction')" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row.function || '-' }}</template>
              </el-table-column>
              <el-table-column prop="timestamp" :label="t('blockchain.colTimestamp')" min-width="165">
                <template #default="{ row }">{{ formatDateTime(row.timestamp) }}</template>
              </el-table-column>
            </el-table>
          </DataPanel>
        </div>

        <DataPanel :title="t('blockchain.statusTitle')" :meta="t('blockchain.statusDescription')">
          <div class="status-row">
            <div class="status-item">
              <span class="status-label">{{ t('blockchain.statusMode') }}</span>
              <strong>{{ chainStatus?.mode || '-' }}</strong>
            </div>
            <div class="status-item">
              <span class="status-label">{{ t('blockchain.statusChannel') }}</span>
              <strong>{{ chainStatus?.channel || '-' }}</strong>
            </div>
            <div class="status-item">
              <span class="status-label">{{ t('blockchain.statusMsp') }}</span>
              <strong>{{ chainStatus?.mspId || '-' }}</strong>
            </div>
            <div class="status-item">
              <span class="status-label">{{ t('blockchain.statusChaincode') }}</span>
              <strong>{{ chainStatus?.chaincode || '-' }}</strong>
            </div>
            <div class="status-item">
              <span class="status-label">{{ t('blockchain.statusPeers') }}</span>
              <strong>{{ formatNumber(chainStatus?.peers ?? 0) }}</strong>
            </div>
          </div>
        </DataPanel>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.chain-ops-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.chain-ops-tabs {
  padding: 0;
}

.browse-grid {
  display: grid;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.status-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--space-3);
  padding: var(--space-4);
}

.status-item {
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
</style>
