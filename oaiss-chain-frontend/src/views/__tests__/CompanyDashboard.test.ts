import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: ref('zh-CN') }),
}))

vi.mock('../../api/carbon', () => ({
  getMyReports: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
}))

vi.mock('../../api/trade', () => ({
  getMyTrades: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
}))

vi.mock('../../api/credit', () => ({
  getMyScore: vi.fn(() => Promise.resolve({ score: 0 })),
}))

vi.mock('../../api/carbonCoin', () => ({
  getMyAccount: vi.fn(() => Promise.resolve({ balance: 0 })),
}))

vi.mock('../../api/enterprise', () => ({
  getQuotaInfo: vi.fn(() => Promise.resolve({ totalQuota: 0 })),
  getMyEnterpriseAdmission: vi.fn(() => Promise.resolve([])),
}))

vi.mock('../../utils/echarts', () => ({
  default: {
    init: vi.fn(() => ({
      setOption: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
    })),
    graphic: {
      LinearGradient: vi.fn(function LinearGradient() {
        return {}
      }),
    },
  },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import CompanyDashboard from '../enterprise/CompanyDashboard.vue'
import { getMyReports } from '../../api/carbon'
import { getMyTrades } from '../../api/trade'
import { getMyScore } from '../../api/credit'
import { getMyAccount } from '../../api/carbonCoin'
import { getQuotaInfo } from '../../api/enterprise'
import { ElMessage } from 'element-plus'
import echarts from '../../utils/echarts'

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
  'el-space': { template: '<div class="el-space"><slot /></div>' },
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
  'el-radio-group': {
    template: '<div class="el-radio-group"><slot /></div>',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
  'el-radio-button': {
    template: '<button class="el-radio-button"><slot /></button>',
    props: ['label'],
  },
  'el-skeleton': { template: '<div class="el-skeleton"></div>', props: ['rows', 'animated'] },
}

function mountComponent() {
  return mount(CompanyDashboard, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('CompanyDashboard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('renders the dashboard shell', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('calls dashboard APIs on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    expect(getMyReports).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100 })
    expect(getMyTrades).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100 })
    expect(getMyScore).toHaveBeenCalled()
    expect(getMyAccount).toHaveBeenCalled()
    expect(getQuotaInfo).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('shows an error message when report loading fails', async () => {
    vi.mocked(getMyReports).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('shows chart containers after loading finishes', async () => {
    vi.mocked(getMyReports).mockResolvedValueOnce({
      items: [{
        reportNo: 'RPT-001',
        reportType: 1,
        accountingPeriod: '2026-Q1',
        scope1Emission: 10,
        scope2Emission: 20,
        scope3Emission: 5,
        totalEmission: 35,
      }],
      total: 1,
    } as never)
    vi.mocked(getMyTrades).mockResolvedValueOnce({
      items: [{
        reportId: 11,
        quantity: 8,
        totalAmount: 100,
        createdAt: '2026-05-22T00:00:00Z',
      }] as never,
      total: 1,
    } as never)
    vi.mocked(getMyScore).mockResolvedValueOnce({ score: 88 } as never)
    vi.mocked(getMyAccount).mockResolvedValueOnce({ balance: 12 } as never)
    vi.mocked(getQuotaInfo).mockResolvedValueOnce({ totalQuota: 50 } as never)

    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.findAll('.chart-box')).toHaveLength(6)
    wrapper.unmount()
  })

  it('builds chart data from real fields without synthetic ratio slices', async () => {
    const initMock = vi.mocked(echarts.init)
    const chartInstance = {
      setOption: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
    }
    vi.mocked(initMock).mockReturnValue(chartInstance as never)

    vi.mocked(getMyReports).mockResolvedValueOnce({
      items: [{
        id: 11,
        reportNo: 'CR-001',
        reportType: 1,
        accountingPeriod: '2026-05',
        createdAt: '2026-05-01T00:00:00Z',
        scope1Emission: 10,
        scope2Emission: 20,
        scope3Emission: 5,
        totalEmission: 35,
      }],
      total: 1,
    } as never)
    vi.mocked(getMyTrades).mockResolvedValueOnce({
      items: [{
        reportId: 11,
        quantity: 8,
        totalAmount: 100,
        createdAt: '2026-05-22T00:00:00Z',
      }] as never,
      total: 1,
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    const options = chartInstance.setOption.mock.calls.map((call) => call[0])
    const emissionPieOption = options.find((option) => option.title?.text === 'companyDashboard.chartEmissionPie')
    const tradePieOption = options.find((option) => option.title?.text === 'companyDashboard.chartTransactionPie')

    expect(emissionPieOption.series[0].data).toEqual([
      { name: 'companyDashboard.pieGasEmission', value: 10 },
      { name: 'companyDashboard.pieWaterEmission', value: 20 },
      { name: 'companyDashboard.pieSolidEmission', value: 5 },
      { name: 'companyDashboard.pieRatedEmission', value: 35 },
    ])
    expect(tradePieOption.series[0].data).toEqual([
      { name: 'companyDashboard.pieTransactionExpense', value: 100 },
      { name: 'companyDashboard.pieTradeQuantity', value: 8 },
    ])
    wrapper.unmount()
  })

  it('filters charts by real asset identifiers instead of fallback asset numbers', async () => {
    const initMock = vi.mocked(echarts.init)
    const chartInstance = {
      setOption: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
    }
    vi.mocked(initMock).mockReturnValue(chartInstance as never)

    vi.mocked(getMyReports).mockResolvedValueOnce({
      items: [
        {
          id: 11,
          reportNo: 'CR-001',
          reportType: 1,
          accountingPeriod: '2026-05',
          createdAt: '2026-05-01T00:00:00Z',
          scope1Emission: 10,
          scope2Emission: 20,
          scope3Emission: 5,
          totalEmission: 35,
        },
        {
          id: 12,
          reportNo: 'CR-002',
          reportType: 2,
          accountingPeriod: '2026-05',
          createdAt: '2026-05-02T00:00:00Z',
          scope1Emission: 30,
          scope2Emission: 10,
          scope3Emission: 0,
          totalEmission: 40,
        },
      ],
      total: 2,
    } as never)
    vi.mocked(getMyTrades).mockResolvedValueOnce({
      items: [
        {
          reportId: 11,
          quantity: 8,
          totalAmount: 100,
          createdAt: '2026-05-22T00:00:00Z',
        },
        {
          reportId: 12,
          quantity: 5,
          totalAmount: 60,
          createdAt: '2026-05-23T00:00:00Z',
        },
      ] as never,
      total: 2,
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('CR-001')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    const options = chartInstance.setOption.mock.calls.map((call) => call[0])
    const latestTradePie = options.filter((option) => option.title?.text === 'companyDashboard.chartTransactionPie').at(-1)

    expect(latestTradePie.series[0].data).toEqual([
      { name: 'companyDashboard.pieTransactionExpense', value: 100 },
      { name: 'companyDashboard.pieTradeQuantity', value: 8 },
    ])
    expect(ElMessage.success).toHaveBeenCalledWith('companyDashboard.filterCompleteN')
    wrapper.unmount()
  })
})
