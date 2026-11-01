import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

vi.mock('../../api/admin', () => ({
  getChainReconciliation: vi.fn(() => Promise.resolve({
    checkedGlobal: 3, checkedPage: 3, matched: 1, missingOnChain: 1, contentMismatch: 0,
    insufficientData: 0, historicalV1: 0, unverifiable: 0, historicalMock: 1,
    total: 3,
    details: [
      { reportId: 1, reportNo: 'CR-1', dbTotalEmission: '100.00', chainTotalEmission: '100.00', status: 'MATCHED' },
      { reportId: 2, reportNo: 'CR-2', dbTotalEmission: '200.00', status: 'MISSING_ON_CHAIN', reason: '链上查无该报告的存证记录' },
      { reportId: 3, reportNo: 'CR-3', status: 'HISTORICAL_MOCK', reason: 'Mock 时期假哈希' },
    ],
  })),
  markHistoricalMockData: vi.fn(() => Promise.resolve({ marked: 3 })),
  getDeadLetters: vi.fn(() => Promise.resolve({
    items: [{
      id: 5, messageId: 'm-5', aggregateType: 'REPORT', aggregateId: 7,
      topic: 'CreateCarbonReportV2', status: 'DEAD', retryCount: 5, errorMessage: 'peer unreachable',
      createdAt: '2026-10-01T10:00:00',
    }],
    total: 1,
  })),
  replayDeadLetter: vi.fn(() => Promise.resolve()),
}))

vi.mock('../../api/blockchain', () => ({
  getStatus: vi.fn(() => Promise.resolve({
    connected: true, mode: 'FABRIC', channel: 'mychannel', mspId: 'Org1MSP',
    chaincode: 'carbon-chaincode', peers: 1,
  })),
  getLatestBlocks: vi.fn(() => Promise.resolve({
    items: [{ blockNumber: 1, blockHash: '0xabc', txCount: 2, timestamp: '2026-10-01T10:00:00' }],
    total: 1,
  })),
  getTransactions: vi.fn(() => Promise.resolve({
    items: [{ txHash: 'tx-1', blockNumber: 1, senderMsp: 'Org1MSP', function: 'CreateCarbonReportV2', status: 'VALID' }],
    total: 1,
  })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import ChainOps from '../admin/ChainOps.vue'
import { getChainReconciliation, getDeadLetters, markHistoricalMockData, replayDeadLetter } from '../../api/admin'
import { getLatestBlocks, getStatus, getTransactions } from '../../api/blockchain'
import { ElMessage } from 'element-plus'

const stubs = {
  'page-header': { template: '<div class="page-header" />', props: ['title', 'description', 'crumbs'] },
  'page-hint': { template: '<div class="page-hint" />', props: ['text'] },
  'stat-strip': { template: '<div class="stat-strip" />', props: ['items', 'loading'] },
  'data-panel': { template: '<div class="data-panel"><slot name="actions" /><slot /><slot name="footer" /></div>', props: ['title', 'meta'] },
  'el-tabs': { template: '<div><slot /></div>', props: ['modelValue'], emits: ['update:modelValue'] },
  'el-tab-pane': { template: '<div><slot /></div>', props: ['label', 'name'] },
  'el-table': { template: '<table><slot /></table>', props: ['data', 'emptyText'] },
  'el-table-column': { template: '<td><slot :row="{}" :$index="0" /></td>', props: ['prop', 'label', 'minWidth', 'width', 'fixed', 'showOverflowTooltip'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type'] },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', props: ['icon', 'loading', 'link', 'type'], emits: ['click'] },
  'el-pagination': { template: '<div />', props: ['currentPage', 'pageSize', 'background', 'pageSizes', 'layout', 'total'], emits: ['size-change', 'current-change'] },
}

function mountComponent() {
  return mount(ChainOps, {
    global: {
      plugins: [createPinia()],
      stubs,
      mocks: { $t: (k: string) => k },
    },
  })
}

describe('ChainOps.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('loads reconciliation, dead letters and ledger browse on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getChainReconciliation).toHaveBeenCalled()
    expect(getDeadLetters).toHaveBeenCalled()
    expect(getStatus).toHaveBeenCalled()
    expect(getLatestBlocks).toHaveBeenCalled()
    expect(getTransactions).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('classifies reconciliation details into matched and missing-on-chain', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(vmOf(wrapper).reconDetails).toHaveLength(3)
    expect(vmOf(wrapper).reconStatusTag('MATCHED')).toBe('success')
    expect(vmOf(wrapper).reconStatusTag('MISSING_ON_CHAIN')).toBe('danger')
    expect(vmOf(wrapper).reconStatusTag('CONTENT_MISMATCH')).toBe('warning')
    // 链上不可达必须是 info，不能当成差异报红——网络故障不等于存证缺失
    expect(vmOf(wrapper).reconStatusTag('UNVERIFIABLE')).toBe('info')

    wrapper.unmount()
  })

  it('surfaces the dead-letter count in the overview strip', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(vmOf(wrapper).deadTotal).toBe(1)
    // 按 label 查找而非数组下标：卡片会增删，下标断言会静默错位
    const stats = vmOf(wrapper).overviewStats as Array<{ label: string; value: number }>
    const deadCard = stats.find((c) => c.value === 1 && c.label)
    expect(deadCard).toBeDefined()
    expect(stats.some((c) => c.value === 1)).toBe(true)

    wrapper.unmount()
  })

  it('replays a dead letter after confirmation and refreshes the list', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await vmOf(wrapper).onReplay({ id: 5, messageId: 'm-5' })
    await flushPromises()

    expect(replayDeadLetter).toHaveBeenCalledWith(5)
    expect(ElMessage.success).toHaveBeenCalled()
    // 挂载时一次 + 重放后刷新一次
    expect(getDeadLetters).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('does not replay when the confirmation is dismissed', async () => {
    const { ElMessageBox } = await import('element-plus')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

    const wrapper = mountComponent()
    await flushPromises()

    await vmOf(wrapper).onReplay({ id: 5, messageId: 'm-5' })
    await flushPromises()

    expect(replayDeadLetter).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('keeps a failure in reconciliation from blanking the page', async () => {
    vi.mocked(getChainReconciliation).mockRejectedValueOnce(new Error('boom'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(vmOf(wrapper).reconResult).toBeNull()
    // 死信与链上浏览不受对账失败影响
    expect(vmOf(wrapper).deadRows).toHaveLength(1)

    wrapper.unmount()
  })

  it('renders historical mock records as a separate, non-alarming category', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    // 历史欠账不能报红，否则真实差异会被永久噪音淹没
    expect(vmOf(wrapper).reconStatusTag('HISTORICAL_MOCK')).toBe('info')
    // 新状态必须有区分度：数据不足需人介入（warning），不能和历史欠账同色
    expect(vmOf(wrapper).reconStatusTag('INSUFFICIENT_DATA')).toBe('warning')
    expect(vmOf(wrapper).reconStatusTag('HISTORICAL_V1')).toBe('info')
    const stats = vmOf(wrapper).overviewStats as Array<{ label: string; value: number }>
    expect(stats.length).toBeGreaterThan(5)
    expect(stats.some((c) => c.value === 1)).toBe(true)

    wrapper.unmount()
  })

  it('marks historical mock data after confirmation and refreshes reconciliation', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await vmOf(wrapper).onMarkHistoricalMock()
    await flushPromises()

    expect(markHistoricalMockData).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalled()
    // 挂载一次 + 标记后刷新一次
    expect(getChainReconciliation).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('does not mark when the confirmation is dismissed', async () => {
    const { ElMessageBox } = await import('element-plus')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')

    const wrapper = mountComponent()
    await flushPromises()

    await vmOf(wrapper).onMarkHistoricalMock()
    await flushPromises()

    expect(markHistoricalMockData).not.toHaveBeenCalled()

    wrapper.unmount()
  })
})
