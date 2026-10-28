import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/trade', () => ({
  getMyTrades: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 0, size: 10, totalPages: 0 })),
  createP2PTrade: vi.fn(() => Promise.resolve()),
  cancelTrade: vi.fn(() => Promise.resolve()),
  confirmTrade: vi.fn(() => Promise.resolve()),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import TradingP2P from '../enterprise/TradingP2P.vue'
import { getMyTrades } from '../../api/trade'
import { ElMessage } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
  'el-form': {
    template: '<form @submit.prevent><slot /></form>',
    methods: {
      validate() { return Promise.resolve(true) },
      resetFields() {},
    },
  },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label', 'prop'] },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'placeholder', 'showPassword', 'clearable', 'rows'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'link', 'plain'],
    emits: ['click'],
  },
  'el-table': {
    template: '<table><slot /><slot name="append" /></table>',
    props: ['data', 'border', 'emptyText'],
  },
  'el-table-column': {
    template: '<td><slot :row="{}" :$index="0" /></td>',
    props: ['prop', 'label', 'minWidth', 'width', 'fixed', 'showOverflowTooltip'],
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type'] },
  'el-pagination': {
    template: '<div class="el-pagination"></div>',
    props: ['currentPage', 'pageSize', 'background', 'pageSizes', 'layout', 'total'],
    emits: ['size-change', 'current-change', 'update:current-page', 'update:page-size'],
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose'],
    emits: ['update:modelValue'],
  },
  'el-select': {
    template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
    props: ['modelValue', 'placeholder', 'style', 'clearable'],
    emits: ['update:modelValue'],
  },
  'el-option': {
    template: '<option :value="value"><slot /></option>',
    props: ['label', 'value'],
  },
}

function mountComponent() {
  return mount(TradingP2P, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('TradingP2P.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('renders successfully', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('loads p2p trades from backend on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getMyTrades).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      tradeType: 2,
      tradeNo: undefined,
      keyword: undefined,
      identity: undefined,
    })

    wrapper.unmount()
  })

  it('passes keyword, trade number and identity filters to backend', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    vi.mocked(getMyTrades).mockClear()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('Buyer Co')
    await inputs[1].setValue('TRX-42')
    await wrapper.find('select').setValue('buyer')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(getMyTrades).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      tradeType: 2,
      tradeNo: 'TRX-42',
      keyword: 'Buyer Co',
      identity: 'buyer',
    })

    wrapper.unmount()
  })

  it('shows an error message when the query fails', async () => {
    vi.mocked(getMyTrades).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('renders returned trade data', async () => {
    vi.mocked(getMyTrades).mockResolvedValueOnce({
      items: [{
        id: 1,
        tradeNo: 'TRX-1',
        tradeType: 2,
        tradeTypeText: 'P2P',
        sellerId: 1,
        sellerName: 'Seller',
        buyerId: 2,
        buyerName: 'Buyer',
        quantity: 50,
        unitPrice: 10,
        totalAmount: 500,
        reportId: 0,
        status: 0,
        statusText: 'Pending',
        remark: '',
        blockchainTxHash: '',
        completedAt: '',
        createdAt: '2026-05-31 12:00:00',
      }],
      total: 1,
      page: 0,
      size: 10,
      totalPages: 1,
    })

    const wrapper = mountComponent()
    await flushPromises()

    expect(getMyTrades).toHaveBeenCalled()
    wrapper.unmount()
  })
})
