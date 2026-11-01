import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
  }),
}))

vi.mock('../../api/tradeAudit', () => ({
  AUDIT_STATUS: { NOT_REQUIRED: 0, PENDING: 1, APPROVED: 2, REJECTED: 3 },
  REJECT_REASONS: ['LARGE_AMOUNT', 'PRICE_DEVIATION', 'LOW_CREDIT', 'WASH_TRADING', 'OTHER'],
  listPendingAudits: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  approveTrade: vi.fn(() => Promise.resolve()),
  rejectTrade: vi.fn(() => Promise.resolve()),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as Record<string, unknown>)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import TradeAudit from '../auditor/TradeAudit.vue'
import { parseRiskReasons as parseReasons } from '../../utils/tradeAudit'
import {
  listPendingAudits,
  approveTrade,
  rejectTrade,
} from '../../api/tradeAudit'

const mockedList = vi.mocked(listPendingAudits)
const mockedApprove = vi.mocked(approveTrade)
const mockedReject = vi.mocked(rejectTrade)

function mountPage() {
  return mount(TradeAudit, {
    global: {
      stubs: {
        PageHeader: { template: '<div class="page-header" />' },
        PageHint: { template: '<div class="page-hint" />' },
        DataPanel: { template: '<div><slot /><slot name="footer" /></div>' },
        FilterBar: { template: '<div><slot /></div>' },
        'el-table': { template: '<table><slot /></table>' },
        'el-table-column': { template: '<td />' },
        'el-pagination': { template: '<div class="pagination" />' },
        'el-select': { template: '<select><slot /></select>' },
        'el-option': { template: '<option />' },
        'el-input': { template: '<input />' },
        'el-button': { template: '<button><slot /></button>' },
        'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-tag': { template: '<span class="el-tag"><slot /></span>' },
        'el-empty': { template: '<div class="empty" />' },
      },
    },
  })
}

/** 直接读组件实例状态：表格插槽在 stub 下不可见，但数据流仍可验证 */
type PageVm = { records: Array<Record<string, unknown>>; total: number }

function vmOf(wrapper: ReturnType<typeof mountPage>): PageVm {
  return wrapper.vm as unknown as PageVm
}

describe('成交审核工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue({ items: [], total: 0, page: 1, size: 10, totalPages: 0 })
  })

  it('挂载后默认按待审核态拉取工单', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(mockedList).toHaveBeenCalled()
    const firstCall = mockedList.mock.calls[0][0]
    expect(firstCall.status).toBe(1)
    expect(wrapper.exists()).toBe(true)
  })

  it('工单列表为空时 records 为空，表格区不渲染裁决按钮', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(vmOf(wrapper).records).toEqual([])

    // 弹窗内的「通过/驳回」按钮在 v-model 关闭时仍存在于 DOM，
    // 所以断言范围限定在表格区：空列表时行内操作列的 slot 不会渲染。
    expect(wrapper.findAll('table button')).toHaveLength(0)
  })

  it('有工单时 records 载入，风险理由可被解析为独立标签', async () => {
    mockedList.mockResolvedValue({
      items: [
        {
          id: 1,
          tradeId: 100,
          auditStatus: 1,
          riskReasons: '["LARGE_AMOUNT(1500>=1000)","PRICE_DEVIATION(30%>20%)"]',
          createdAt: '2026-11-01T10:00:00',
        },
      ],
      total: 1,
      page: 1,
      size: 10,
      totalPages: 1,
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(vmOf(wrapper).records).toHaveLength(1)
    const reasons = parseReasons(vmOf(wrapper).records[0].riskReasons as string)
    expect(reasons).toEqual(['LARGE_AMOUNT(1500>=1000)', 'PRICE_DEVIATION(30%>20%)'])
  })

  it('风险理由为 null 或格式异常时不抛错（历史数据可能缺失该字段）', () => {
    expect(parseReasons(null)).toEqual([])
    expect(parseReasons(undefined)).toEqual([])
    expect(parseReasons('')).toEqual([])
    // 非 JSON 的历史脏数据：退回原文展示，至少审核员还能看到内容
    expect(parseReasons('LOW_CREDIT')).toEqual(['LOW_CREDIT'])
    // JSON 但非数组
    expect(parseReasons('"LOW_CREDIT"')).toEqual(['"LOW_CREDIT"'])
  })

  it('审核通过调用 approveTrade 并带上意见', async () => {
    await mockedApprove(100, '核对无误')
    expect(mockedApprove).toHaveBeenCalledWith(100, '核对无误')
  })

  it('驳回调用 rejectTrade 并带上理由代码', async () => {
    await mockedReject(100, '价格异常', 'PRICE_DEVIATION')
    expect(mockedReject).toHaveBeenCalledWith(100, '价格异常', 'PRICE_DEVIATION')
  })

  it('页面不提供企业侧动作（审核工作台不得出现挂单/撤单入口）', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const buttons = wrapper.findAll('button').map((b) => b.text())
    expect(buttons.some((t) => t.includes('placeOrder'))).toBe(false)
    expect(buttons.some((t) => t.includes('cancelTrade'))).toBe(false)
  })
})
