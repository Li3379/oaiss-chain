import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('../../api/thirdParty', () => ({
  getCarbonReports: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  getStatistics: vi.fn(() => Promise.resolve({
    totalReports: 0,
    pendingReports: 0,
    approvedReports: 0,
    rejectedReports: 0,
  })),
  getOrgInfo: vi.fn(() => Promise.resolve({
    orgName: '监管机构A',
    accessLevel: 2,
    address: 'Shanghai',
    contactPerson: 'Alice',
    contactPhone: '13800138000',
  })),
  updateContact: vi.fn(() => Promise.resolve()),
}))

vi.mock('../../api/trade', () => ({
  listTrades: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
}))

vi.mock('../../api/regulatoryFinding', () => ({
  FINDING_SEVERITY: { LOW: 'LOW', MEDIUM: 'MEDIUM', HIGH: 'HIGH', CRITICAL: 'CRITICAL' },
  FINDING_RULES: {
    REPEATED_REJECTION: 'REPEATED_REJECTION',
    QUOTA_OVERDRAFT: 'QUOTA_OVERDRAFT',
    CREDIT_PLUNGE: 'CREDIT_PLUNGE',
    WASH_TRADING_BLOCKED: 'WASH_TRADING_BLOCKED',
  },
  listFindings: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  resolveFinding: vi.fn(() => Promise.resolve({})),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import Monitor from '../third-party/Monitor.vue'
import { getCarbonReports, getOrgInfo, getStatistics, updateContact } from '../../api/thirdParty'
import { listTrades } from '../../api/trade'
import { ElMessage } from 'element-plus'

const stubs = {
  'page-container': { template: '<div class="page-container"><slot /></div>', props: ['title', 'description'] },
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>' },
  'el-form': { template: '<form @submit.prevent><slot /></form>' },
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
  return mount(Monitor, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('Monitor.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('loads reports, statistics, and org info on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getCarbonReports).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      enterpriseId: undefined,
      keyword: undefined,
      status: undefined,
    })
    expect(getStatistics).toHaveBeenCalled()
    expect(getOrgInfo).toHaveBeenCalled()
    expect(listTrades).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      status: undefined,
      tradeType: undefined,
    })
    wrapper.unmount()
  })

  it('forwards filter params to the backend report query', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[2].setValue('12')
    await inputs[3].setValue('annual')
    await wrapper.find('select').setValue('3')
    ;(wrapper.vm as unknown as { onSearch: () => void }).onSearch()
    await flushPromises()

    expect(getCarbonReports).toHaveBeenNthCalledWith(2, {
      pageNum: 1,
      pageSize: 10,
      enterpriseId: 12,
      keyword: 'annual',
      status: '3',
    })
    wrapper.unmount()
  })

  it('hydrates org info and statistics into the page state', async () => {
    vi.mocked(getStatistics).mockResolvedValueOnce({
      totalReports: 20,
      pendingReports: 5,
      approvedReports: 12,
      rejectedReports: 3,
      orgName: '监管机构A',
      accessLevel: 2,
    })

    const wrapper = mountComponent()
    await flushPromises()

    expect((wrapper.vm as unknown as { statistics: { totalReports: number } }).statistics.totalReports).toBe(20)
    expect((wrapper.vm as unknown as { orgInfo: { orgName: string } | null }).orgInfo?.orgName).toBe('监管机构A')
    wrapper.unmount()
  })

  it('saves contact info and refreshes org info on success', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('Bob')
    await inputs[1].setValue('13900139000')
    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()

    expect(updateContact).toHaveBeenCalledWith({
      contactPerson: 'Bob',
      contactPhone: '13900139000',
    })
    expect(ElMessage.success).toHaveBeenCalledWith('monitor.contactSaveSuccess')
    expect(getOrgInfo).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('shows an error message when report loading fails', async () => {
    vi.mocked(getCarbonReports).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalledWith('monitor.loadFailed')
    wrapper.unmount()
  })

  it('blocks invalid contact submissions before calling the backend', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('')
    await inputs[1].setValue('abc')
    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()

    expect(updateContact).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('monitor.contactSaveFailed')
    wrapper.unmount()
  })

  it('renders the regulator trade ledger with full trade detail columns', async () => {
    vi.mocked(listTrades).mockResolvedValueOnce({
      items: [
        {
          id: 1,
          tradeNo: 'TX-2024-001',
          tradeType: 2,
          tradeTypeText: 'P2P交易',
          sellerId: 1,
          sellerName: '华能发电有限公司',
          buyerId: 2,
          buyerName: '国网能源有限公司',
          quantity: 100,
          unitPrice: 50,
          totalAmount: 5000,
          reportId: 1,
          status: 2,
          statusText: '已完成',
          remark: '',
          blockchainTxHash: 'abc123',
          completedAt: '2026-01-02T10:00:00',
          createdAt: '2026-01-01T10:00:00',
        },
      ],
      total: 1,
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    expect((wrapper.vm as unknown as { trades: unknown[] }).trades).toHaveLength(1)
    expect((wrapper.vm as unknown as { tradeTotal: number }).tradeTotal).toBe(1)
    // 交易类型文案走前端 i18n（en 界面不会混入后端中文）
    expect((wrapper.vm as unknown as { tradeTypeLabel: (t: number) => string }).tradeTypeLabel(2))
      .toBe('monitor.tradeTypeP2P')
    wrapper.unmount()
  })

  it('forwards the status/trade-type filters to the trade ledger query', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const selects = wrapper.findAll('select')
    await selects[1].setValue('2')
    await selects[2].setValue('1')
    ;(wrapper.vm as unknown as { onTradeSearch: () => void }).onTradeSearch()
    await flushPromises()

    expect(listTrades).toHaveBeenLastCalledWith({
      page: 1,
      size: 10,
      status: '2',
      tradeType: '1',
    })
    wrapper.unmount()
  })

  it('keeps the trade ledger read-only (no create/cancel/confirm actions)', async () => {
    vi.mocked(getStatistics).mockResolvedValueOnce({
      totalReports: 1,
      pendingReports: 0,
      approvedReports: 1,
      rejectedReports: 0,
      totalTrades: 1,
      totalTradeQuantity: 100,
      totalTradeAmount: 5000,
      orgName: '监管机构A',
      accessLevel: 2,
    })
    const wrapper = mountComponent()
    await flushPromises()

    const tradeApi = wrapper.vm as unknown as Record<string, unknown>
    expect(tradeApi.createP2PTrade).toBeUndefined()
    expect(tradeApi.cancelTrade).toBeUndefined()
    expect(tradeApi.confirmTrade).toBeUndefined()
    // 页面只调用了统计与交易列表这两个读接口
    expect(listTrades).toHaveBeenCalled()
    expect((wrapper.vm as unknown as { statistics: { totalTrades?: number } }).statistics.totalTrades).toBe(1)
    wrapper.unmount()
  })

  it('监管线索区加载未处置线索（只呈现，不改业务状态）', async () => {
    const findingApi = await import('../../api/regulatoryFinding')
    const mockedListFindings = vi.mocked(findingApi.listFindings)
    mockedListFindings.mockResolvedValue({
      items: [
        {
          id: 1,
          enterpriseId: 10,
          ruleCode: 'REPEATED_REJECTION',
          severity: 'HIGH',
          evidence: '近 30 日成交被驳回 5 次（阈值 3 次）',
          resolved: false,
        },
      ],
      total: 1,
      page: 1,
      size: 10,
      totalPages: 1,
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    expect(mockedListFindings).toHaveBeenCalled()
    const vm = wrapper.vm as unknown as { findings: Array<Record<string, unknown>>; findingTotal: number }
    expect(vm.findings).toHaveLength(1)
    expect(vm.findings[0].ruleCode).toBe('REPEATED_REJECTION')
    expect(vm.findingTotal).toBe(1)
    wrapper.unmount()
  })

  it('线索区不提供任何影响企业状态的写操作（监管只发现不处置企业）', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as Record<string, unknown>
    // 冻结/扣分/下市这些动作不应出现在监管页面
    expect(vm.freezeAccount).toBeUndefined()
    expect(vm.deductCredit).toBeUndefined()
    expect(vm.delistEnterprise).toBeUndefined()
    wrapper.unmount()
  })
})
