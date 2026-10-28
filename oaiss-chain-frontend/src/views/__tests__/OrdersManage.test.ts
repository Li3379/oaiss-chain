// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
// 注意：行首勿用 "(wrapper.vm as any)"——与上行 await 组合会触发 ASI 解析陷阱。
function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/trade', () => ({
  getMyTrades: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  cancelTrade: vi.fn(() => Promise.resolve()),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import OrdersManage from '../enterprise/OrdersManage.vue'
import { getMyTrades, cancelTrade } from '../../api/trade'
import { ElMessage, ElMessageBox } from 'element-plus'

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
  return mount(OrdersManage, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('OrdersManage.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('缁勪欢姝ｇ‘娓叉煋', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('椤甸潰鍔犺浇鏃惰皟鐢ˋPI', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    expect(getMyTrades).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('API璋冪敤澶辫触鏄剧ず閿欒娑堟伅', async () => {
    vi.mocked(getMyTrades).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('缁勪欢娓叉煋鏁版嵁', async () => {
    vi.mocked(getMyTrades).mockResolvedValueOnce({
      items: [{ id: 1, status: 0 }] as never,
      total: 1,
    } as never)
    const wrapper = mountComponent()
    await flushPromises()
    expect(getMyTrades).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not apply an extra client-side tradeType filter after server pagination', async () => {
    vi.mocked(getMyTrades).mockResolvedValueOnce({
      items: [
        {
          id: 1,
          tradeNo: 'TRX-001',
          tradeType: 1,
          tradeTypeText: 'Auction Trade',
          sellerId: 10,
          sellerName: 'Seller A',
          buyerId: 20,
          buyerName: 'Buyer A',
          quantity: 1,
          unitPrice: 2,
          totalAmount: 2,
          reportId: 0,
          status: 0,
          statusText: 'Pending',
          remark: '',
          blockchainTxHash: '',
          completedAt: '',
          createdAt: '2026-05-31 10:00:00',
        },
        {
          id: 2,
          tradeNo: 'TRX-002',
          tradeType: 2,
          tradeTypeText: 'P2P Trade',
          sellerId: 11,
          sellerName: 'Seller B',
          buyerId: 21,
          buyerName: 'Buyer B',
          quantity: 3,
          unitPrice: 4,
          totalAmount: 12,
          reportId: 0,
          status: 2,
          statusText: 'Completed',
          remark: '',
          blockchainTxHash: '',
          completedAt: '',
          createdAt: '2026-05-31 11:00:00',
        },
      ],
      total: 2,
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).searchForm.tradeType = 1
    await vmOf(wrapper).$nextTick()

    expect(vmOf(wrapper).tableData).toHaveLength(2)
    wrapper.unmount()
  })
})
