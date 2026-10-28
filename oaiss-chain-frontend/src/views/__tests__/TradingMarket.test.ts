// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
// 注意：行首勿用 "(wrapper.vm as any)"——与上行 await 组合会触发 ASI 解析陷阱。
function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/auction', () => ({
  submitBuyOrder: vi.fn(),
  submitSellOrder: vi.fn(),
  getAuctionOrders: vi.fn(),
  getMyOrders: vi.fn(),
  getMatchResults: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import TradingMarket from '../enterprise/TradingMarket.vue'
import { getAuctionOrders, getMatchResults, getMyOrders, submitBuyOrder, submitSellOrder } from '../../api/auction'
import { ElMessage } from 'element-plus'

const orderRows = [
  {
    id: 1,
    orderNo: 'AUC-001',
    userId: 7,
    direction: 1,
    directionText: 'BUY',
    quantity: 10,
    price: 88,
    matchedQuantity: 2,
    remainingQuantity: 8,
    status: 0,
    statusText: 'Pending',
    settlementPrice: 0,
    matchedAt: '2026-05-31T08:30:00',
    createdAt: '2026-05-31T08:00:00',
  },
]

const myOrderRows = [
  {
    ...orderRows[0],
    id: 2,
    orderNo: 'AUC-MY-001',
  },
]

const matchingRows = [
  {
    id: 11,
    matchNo: 'MATCH-001',
    buyOrderId: 100,
    sellOrderId: 101,
    buyerId: 7,
    sellerId: 9,
    buyerName: 'Buyer Co',
    sellerName: 'Seller Co',
    matchedQuantity: 6,
    settlementPrice: 90,
    totalAmount: 540,
    status: 1,
    statusText: 'Matched',
    transactionId: 5001,
    settledAt: '2026-05-31T09:00:00',
    createdAt: '2026-05-31T08:50:00',
  },
]

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
    props: ['modelValue', 'type', 'placeholder', 'showPassword', 'clearable', 'rows', 'min', 'step'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'link', 'plain'],
    emits: ['click'],
  },
  'el-table': {
    template: '<table><slot /></table>',
    props: ['data', 'border', 'emptyText', 'loading'],
    emits: ['selection-change'],
  },
  'el-table-column': {
    template: '<td><slot :row="row" :$index="0" /></td>',
    props: ['type', 'prop', 'label', 'minWidth', 'width', 'fixed', 'showOverflowTooltip'],
    data() {
      return { row: orderRows[0] }
    },
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
    template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
    props: ['modelValue', 'placeholder', 'style'],
    emits: ['update:modelValue'],
  },
  'el-option': {
    template: '<option :value="value"><slot /></option>',
    props: ['label', 'value'],
  },
  'el-tabs': {
    template: '<div class="el-tabs"><slot /></div>',
    props: ['modelValue'],
    emits: ['tab-change', 'update:modelValue'],
  },
  'el-tab-pane': {
    template: '<div class="el-tab-pane"></div>',
    props: ['label', 'name'],
  },
}

function mountComponent() {
  return mount(TradingMarket, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('TradingMarket.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.mocked(getAuctionOrders).mockResolvedValue({
      items: orderRows,
      total: 1,
      page: 1,
      size: 10,
      totalPages: 1,
    })
    vi.mocked(getMyOrders).mockResolvedValue({
      items: myOrderRows,
      total: 1,
      page: 1,
      size: 10,
      totalPages: 1,
    })
    vi.mocked(getMatchResults).mockResolvedValue({
      items: matchingRows,
      total: 1,
      page: 1,
      size: 10,
      totalPages: 1,
    })
    vi.mocked(submitBuyOrder).mockResolvedValue(orderRows[0])
    vi.mocked(submitSellOrder).mockResolvedValue({
      ...orderRows[0],
      direction: 2,
      directionText: 'SELL',
    })
  })

  it('loads auction orders on mount using the paged backend contract', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getAuctionOrders).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(vmOf(wrapper).tableData).toEqual(orderRows)
    expect(vmOf(wrapper).total).toBe(1)
    expect(wrapper.find('.table-tip').text()).toContain('1')

    wrapper.unmount()
  })

  it('correctly displays total in table-tip instead of selectedRows.length (C-1 defect fix)', async () => {
    vi.mocked(getAuctionOrders).mockResolvedValueOnce({
      items: orderRows,
      total: 42,
      page: 1,
      size: 10,
      totalPages: 5,
    })
    const wrapper = mountComponent()
    await flushPromises()

    expect(vmOf(wrapper).total).toBe(42)
    expect(vmOf(wrapper).selectedRows).toEqual([])
    const tip = wrapper.find('.table-tip')
    expect(tip.exists()).toBe(true)
    expect(tip.text()).toContain('42')
    expect(tip.text()).not.toContain('共 0 条')

    wrapper.unmount()
  })

  it('switches to my orders and matched results using the correct backend endpoints', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).activeTab = 'my'
    await vmOf(wrapper).onTabChange()
    await flushPromises()
    expect(getMyOrders).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(vmOf(wrapper).tableData).toEqual(myOrderRows)

    vmOf(wrapper).activeTab = 'match'
    await vmOf(wrapper).onTabChange()
    await flushPromises()
    expect(getMatchResults).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(vmOf(wrapper).matchData).toEqual(matchingRows)
    expect(vmOf(wrapper).matchTotal).toBe(1)

    wrapper.unmount()
  })

  it('submits a buy order and refreshes the backend list', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).formRef = {
      validate: vi.fn(() => Promise.resolve(true)),
    }
    vmOf(wrapper).formModel.direction = 1
    vmOf(wrapper).formModel.quantity = '12.5'
    vmOf(wrapper).formModel.price = '87.6'

    await vmOf(wrapper).onSave()
    await flushPromises()

    expect(submitBuyOrder).toHaveBeenCalledWith({
      direction: 1,
      quantity: 12.5,
      price: 87.6,
    })
    expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('买单提交成功'))
    expect(getAuctionOrders).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('submits a sell order when the form direction is sell', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).formRef = {
      validate: vi.fn(() => Promise.resolve(true)),
    }
    vmOf(wrapper).formModel.direction = 2
    vmOf(wrapper).formModel.quantity = '6'
    vmOf(wrapper).formModel.price = '99'

    await vmOf(wrapper).onSave()
    await flushPromises()

    expect(submitSellOrder).toHaveBeenCalledWith({
      direction: 2,
      quantity: 6,
      price: 99,
    })
    expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('卖单提交成功'))

    wrapper.unmount()
  })

  it('shows an error when loading auction orders fails', async () => {
    vi.mocked(getAuctionOrders).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('获取数据失败')
    expect(vmOf(wrapper).tableData).toEqual([])
    expect(vmOf(wrapper).total).toBe(0)

    wrapper.unmount()
  })
})
