// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
// 注意：行首勿用 "(wrapper.vm as any)"——与上行 await 组合会触发 ASI 解析陷阱。
function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/blockchain', () => ({
  getStatus: vi.fn(() => Promise.resolve({
    connected: true,
    mode: 'FABRIC',
    channel: 'carbon-channel',
    peers: 1,
    orderers: 1,
  })),
  getLatestBlocks: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  getTransactions: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  queryBlock: vi.fn(() => Promise.resolve({ blockNumber: 0, txCount: 1 })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import Blockchain from '../enterprise/Blockchain.vue'
import { getLatestBlocks, getStatus, getTransactions, queryBlock } from '../../api/blockchain'
import { ElMessage } from 'element-plus'

const stubs = {
  'page-container': { template: '<div class="page-container"><slot /></div>', props: ['title', 'description'] },
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-table': {
    template: '<table><slot /></table>',
    props: ['data', 'border', 'emptyText'],
  },
  'el-table-column': {
    template: '<td><slot :row="{}" :$index="0" /></td>',
    props: ['prop', 'label', 'minWidth', 'width', 'fixed', 'showOverflowTooltip'],
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type'] },
  'el-tabs': { template: '<div class="el-tabs"><slot /></div>', props: ['modelValue'], emits: ['update:modelValue'] },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
  'el-pagination': {
    template: '<div class="el-pagination"></div>',
    props: ['currentPage', 'pageSize', 'background', 'pageSizes', 'layout', 'total'],
    emits: ['size-change', 'current-change', 'update:current-page', 'update:page-size'],
  },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @keyup.enter="$emit(\'keyup.enter\')" />',
    props: ['modelValue', 'placeholder', 'clearable'],
    emits: ['update:modelValue', 'keyup.enter'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'loading'],
    emits: ['click'],
  },
  'el-alert': { template: '<div class="el-alert">{{ title }}</div>', props: ['type', 'closable', 'title'] },
}

function mountComponent() {
  return mount(Blockchain, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('Blockchain.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('loads blockchain status, blocks, and transactions on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getStatus).toHaveBeenCalled()
    expect(getLatestBlocks).toHaveBeenCalled()
    expect(getTransactions).toHaveBeenCalled()
    expect(vmOf(wrapper).chainStatus).toMatchObject({
      connected: true,
      mode: 'FABRIC',
      channel: 'carbon-channel',
    })

    wrapper.unmount()
  })

  it('shows an error when status loading fails', async () => {
    vi.mocked(getStatus).mockRejectedValueOnce(new Error('status failed'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(vmOf(wrapper).chainStatus).toBeNull()

    wrapper.unmount()
  })

  // 交易哈希查询面板已移除（2026-10-31）。链上交易统一从「交易记录」列表下钻，
  // 因此页面不再暴露 txHashQuery / submitTxQuery 这组状态与方法。
  it('no longer exposes the removed transaction-hash lookup panel', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect('txHashQuery' in vmOf(wrapper)).toBe(false)
    expect('submitTxQuery' in vmOf(wrapper)).toBe(false)
    expect(wrapper.find('[data-testid="blockchain-tx-query-input"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="blockchain-tx-query-result"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('drills into a block and shows its transactions', async () => {
    vi.mocked(queryBlock).mockResolvedValueOnce({
      blockNumber: 3,
      txCount: 1,
      channel: 'mychannel',
      transactions: [{ txHash: 'tx-1', senderMsp: 'Org1MSP', function: 'CreateCarbonReportV2', status: 'VALID' }],
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    await vmOf(wrapper).openBlockDetail(3)
    await flushPromises()

    expect(queryBlock).toHaveBeenCalledWith(3)
    expect(vmOf(wrapper).blockDetailTransactions).toHaveLength(1)
    expect(vmOf(wrapper).blockDetailTransactions[0].function).toBe('CreateCarbonReportV2')

    wrapper.unmount()
  })

  it('renders the real network status panel without the lookup column', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(vmOf(wrapper).chainStatus).toMatchObject({ channel: 'carbon-channel' })
    expect(wrapper.find('[data-testid="blockchain-status-tag"]').exists()).toBe(true)

    wrapper.unmount()
  })
})
