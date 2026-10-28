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
  queryTransaction: vi.fn(() => Promise.resolve({
    txHash: 'tx-001',
    status: 'VALID',
    timestamp: '2026-05-31T10:00:00',
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

import Blockchain from '../enterprise/Blockchain.vue'
import { getLatestBlocks, getStatus, getTransactions, queryTransaction } from '../../api/blockchain'
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

  it('requires a transaction hash before querying', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).txHashQuery = '   '
    await vmOf(wrapper).submitTxQuery()

    expect(queryTransaction).not.toHaveBeenCalled()
    expect(vmOf(wrapper).txQueryError).toBeTruthy()

    wrapper.unmount()
  })

  it('parses transaction query responses into structured result data', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).txHashQuery = 'tx-001'
    await vmOf(wrapper).submitTxQuery()

    expect(queryTransaction).toHaveBeenCalledWith('tx-001')
    expect(vmOf(wrapper).txQueryResult).toMatchObject({
      txHash: 'tx-001',
      status: 'VALID',
    })

    wrapper.unmount()
  })
})
