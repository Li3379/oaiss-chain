// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
// 注意：行首勿用 "(wrapper.vm as any)"——与上行 await 组合会触发 ASI 解析陷阱。
function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/auction', () => ({
  executeMatching: vi.fn(),
  getAllMatchResults: vi.fn(),
  getMatchingStatus: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  }
})

import AuctionMatching from '../admin/AuctionMatching.vue'
import { executeMatching, getAllMatchResults, getMatchingStatus } from '../../api/auction'
import { ElMessage } from 'element-plus'

const statusPayload = {
  autoMatchingEnabled: true,
  intervalSeconds: 30,
  pendingBuyOrders: 3,
  pendingSellOrders: 2,
  lastMatchAt: '2026-10-28T10:00:00',
}

const matchRows = [
  {
    id: 1,
    matchNo: 'MT20261028001',
    buyOrderId: 11,
    sellOrderId: 12,
    buyerId: 1,
    sellerId: 2,
    buyerName: 'Buyer Co',
    sellerName: 'Seller Co',
    matchedQuantity: 10,
    settlementPrice: 60,
    totalAmount: 600,
    status: 1,
    statusText: 'Settled',
    transactionId: 500,
    settledAt: '2026-10-28T10:00:00',
    createdAt: '2026-10-28T10:00:00',
  },
]

const stubs = {
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'icon', 'link', 'plain'],
    emits: ['click'],
  },
  'el-table': {
    template: '<table><slot /></table>',
    props: ['data', 'border', 'emptyText', 'loading'],
  },
  'el-table-column': {
    template: '<td><slot :row="row" :$index="0" /></td>',
    props: ['type', 'prop', 'label', 'minWidth', 'width', 'fixed', 'showOverflowTooltip'],
    data() {
      return { row: matchRows[0] }
    },
  },
  'el-pagination': {
    template: '<div class="el-pagination"></div>',
    props: ['currentPage', 'pageSize', 'background', 'pageSizes', 'layout', 'total'],
    emits: ['size-change', 'current-change', 'update:current-page', 'update:page-size'],
  },
}

function mountComponent() {
  return mount(AuctionMatching, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('AuctionMatching.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.mocked(getMatchingStatus).mockResolvedValue({ ...statusPayload })
    vi.mocked(getAllMatchResults).mockResolvedValue({
      items: matchRows,
      total: 1,
      page: 1,
      size: 10,
      totalPages: 1,
    } as never)
  })

  it('挂载即加载撮合运行态与全站成交明细', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getMatchingStatus).toHaveBeenCalled()
    expect(getAllMatchResults).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(vmOf(wrapper).status.autoMatchingEnabled).toBe(true)
    expect(vmOf(wrapper).matchRows).toEqual(matchRows)

    wrapper.unmount()
  })

  it('手动撮合成功时提示本轮成交笔数，并刷新状态与明细', async () => {
    vi.mocked(executeMatching).mockResolvedValue([matchRows[0]] as never)

    const wrapper = mountComponent()
    await flushPromises()
    vi.mocked(getMatchingStatus).mockClear()
    vi.mocked(getAllMatchResults).mockClear()

    await vmOf(wrapper).onMatchNow()
    await flushPromises()

    expect(executeMatching).toHaveBeenCalledTimes(1)
    expect(vi.mocked(ElMessage).success).toHaveBeenCalled()
    expect(getMatchingStatus).toHaveBeenCalled()
    expect(getAllMatchResults).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('没有可成交挂单时给出信息提示而非成功', async () => {
    vi.mocked(executeMatching).mockResolvedValue([] as never)

    const wrapper = mountComponent()
    await flushPromises()

    await vmOf(wrapper).onMatchNow()
    await flushPromises()

    expect(vi.mocked(ElMessage).info).toHaveBeenCalled()
    expect(vi.mocked(ElMessage).success).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('撮合失败时提示错误且不抛出（锁竞争场景）', async () => {
    vi.mocked(executeMatching).mockRejectedValue(new Error('OPERATION_IN_PROGRESS'))

    const wrapper = mountComponent()
    await flushPromises()

    await expect(vmOf(wrapper).onMatchNow()).resolves.toBeUndefined()
    expect(vi.mocked(ElMessage).error).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('连点只触发一次撮合（同步置位防抖）', async () => {
    let resolveMatch: (v: unknown) => void = () => {}
    vi.mocked(executeMatching).mockReturnValue(
      new Promise((resolve) => { resolveMatch = resolve }) as never,
    )

    const wrapper = mountComponent()
    await flushPromises()

    const first = vmOf(wrapper).onMatchNow()
    const second = vmOf(wrapper).onMatchNow()
    resolveMatch([])
    await Promise.all([first, second])
    await flushPromises()

    expect(executeMatching).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })
})
