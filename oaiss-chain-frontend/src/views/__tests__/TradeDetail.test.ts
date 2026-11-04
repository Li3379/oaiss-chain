import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('../../api/trade', () => ({
  getTrade: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: vi.fn(() => ({ params: { id: '1' } })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  }
})

import TradeDetail from '../enterprise/TradeDetail.vue'
import { getTrade } from '../../api/trade'

const stubs = {
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border'] },
  'el-descriptions-item': {
    template: '<div class="el-descriptions-item"><span class="label">{{ label }}</span><slot /></div>',
    props: ['label', 'span'],
  },
  'el-steps': { template: '<div class="el-steps"><slot /></div>', props: ['active', 'finishStatus', 'alignCenter'] },
  'el-step': { template: '<div class="el-step">{{ title }}|{{ description }}</div>', props: ['title', 'description'] },
  'el-empty': { template: '<div class="el-empty">{{ description }}<slot /></div>', props: ['description'] },
  'el-button': { template: '<button><slot /></button>', props: ['type', 'size', 'link', 'plain'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
}

const fullTrade = {
  id: 1,
  tradeNo: 'TRX-2026-000042',
  tradeType: 1,
  tradeTypeText: '拍卖交易',
  sellerId: 10,
  sellerName: '甲方能源',
  buyerId: 20,
  buyerName: '乙方制造',
  quantity: 1200,
  unitPrice: 88.5,
  totalAmount: 106200,
  reportId: 66,
  status: 2,
  statusText: '已完成',
  remark: '季度配额调剂',
  blockchainTxHash: 'tx-should-never-render-9f3a',
  completedAt: '2026-10-02T15:04:05',
  createdAt: '2026-10-01T09:00:00',
  auditStatus: 2,
}

function mountDetail() {
  return mount(TradeDetail, {
    global: { stubs },
    attachTo: document.body,
  })
}

async function flush() {
  await nextTick()
  await nextTick()
}

describe('TradeDetail.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('根据路由参数加载交易并渲染四个分区', async () => {
    vi.mocked(getTrade).mockResolvedValueOnce(fullTrade as never)

    const wrapper = mountDetail()
    await flush()

    expect(getTrade).toHaveBeenCalledWith(1)
    const text = wrapper.text()
    expect(text).toContain('基本信息')
    expect(text).toContain('核算关联')
    expect(text).toContain('状态时间线')
    expect(text).toContain('备注与审计')
    expect(text).toContain('TRX-2026-000042')

    wrapper.unmount()
  })

  it('未找到交易时渲染空态且不抛出未捕获异常', async () => {
    vi.mocked(getTrade).mockRejectedValueOnce(new Error('not found'))

    const wrapper = mountDetail()
    await flush()

    expect(wrapper.find('.el-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('未找到该交易')
    expect(wrapper.text()).not.toContain('基本信息')

    wrapper.unmount()
  })

  it('不渲染区块链哈希', async () => {
    vi.mocked(getTrade).mockResolvedValueOnce(fullTrade as never)

    const wrapper = mountDetail()
    await flush()

    expect(wrapper.text()).not.toContain('tx-should-never-render-9f3a')
    expect(wrapper.html()).not.toContain('blockchainTxHash')

    wrapper.unmount()
  })

  it('核算关联按 reportId 渲染链接或占位符', async () => {
    vi.mocked(getTrade).mockResolvedValueOnce(fullTrade as never)
    const withReport = mountDetail()
    await flush()
    expect(withReport.find('a[href="/enterprise/carbon/upload"]').exists()).toBe(true)
    expect(withReport.text()).toContain('#66')
    withReport.unmount()

    vi.mocked(getTrade).mockResolvedValueOnce({ ...fullTrade, reportId: 0 } as never)
    const withoutReport = mountDetail()
    await flush()
    expect(withoutReport.find('a[href="/enterprise/carbon/upload"]').exists()).toBe(false)
    withoutReport.unmount()
  })
})
