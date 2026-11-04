import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/carbon', () => ({
  getMyReports: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  createReport: vi.fn(() => Promise.resolve()),
  deleteReport: vi.fn(() => Promise.resolve()),
  submitReport: vi.fn(() => Promise.resolve()),
  getAiReportAnalysis: vi.fn(),
  getReport: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import CarbonUpload from '../enterprise/CarbonUpload.vue'
import { getMyReports, createReport, deleteReport, submitReport, getAiReportAnalysis, getReport } from '../../api/carbon'
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
  'el-input-number': {
    template: '<input class="el-input-number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
    props: ['modelValue', 'min', 'max', 'step', 'precision', 'controls', 'placeholder', 'ariaLabel'],
    emits: ['update:modelValue'],
  },
  'el-radio-group': {
    template:
      '<div class="el-radio-group">' +
      '<button class="mode-structured" @click="$emit(\'update:modelValue\', \'structured\')">structured</button>' +
      '<button class="mode-json" @click="$emit(\'update:modelValue\', \'json\')">json</button>' +
      '<slot />' +
      '</div>',
    props: ['modelValue', 'size'],
    emits: ['update:modelValue'],
  },
  'el-radio-button': { template: '<span class="el-radio-button"><slot /></span>', props: ['value'] },
  'el-tooltip': { template: '<span class="el-tooltip"><slot /></span>', props: ['content', 'placement', 'effect'] },
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
    props: ['modelValue', 'placeholder', 'style'],
    emits: ['update:modelValue'],
  },
  'el-option': {
    template: '<option :value="value"><slot /></option>',
    props: ['label', 'value'],
  },
  'el-alert': {
    template: '<div class="el-alert" :data-testid="$attrs[\'data-testid\']">{{ title }}|{{ description }}</div>',
    props: ['title', 'description', 'type', 'closable', 'showIcon'],
    inheritAttrs: false,
  },
}

function mountComponent() {
  return mount(CarbonUpload, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('CarbonUpload.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('组件正确渲染', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('页面加载时获取碳核算报告列表', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    expect(getMyReports).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('获取报告失败显示错误消息', async () => {
    vi.mocked(getMyReports).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('组件渲染报告列表数据', async () => {
    vi.mocked(getMyReports).mockResolvedValueOnce({
      items: [{ id: 1, title: '报告1', status: 0 }] as never,
      total: 1,
    } as never)
    const wrapper = mountComponent()
    await flushPromises()
    expect(getMyReports).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('结构化填写排放数据：自动生成 JSON 与合计（ELI5 改造回归）', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    // 打开创建对话框（搜索栏的「创建」按钮）
    const createButton = wrapper.findAll('button').find((btn) => btn.text() === '创建')
    expect(createButton).toBeTruthy()
    await createButton!.trigger('click')
    await flushPromises()

    const dialog = wrapper.find('.el-dialog')
    expect(dialog.exists()).toBe(true)
    expect(dialog.find('[data-testid="structured-emission"]').exists()).toBe(true)

    // 填写范围一一行：100 t x 2.5 = 250
    //
    // 不能用 findAll('.el-input-number')[0]/[1] 定位——组件 1 在表单里新增了
    // 「发电量」输入框（同样是 el-input-number），会把索引挤位。
    // 改为在排放编辑器内部按索引找，与该区域之外的字段解耦。
    const emissionEditor = dialog.find('[data-testid="structured-emission"]')
    const numbers = emissionEditor.findAll('.el-input-number')
    expect(numbers.length).toBeGreaterThanOrEqual(2)
    await numbers[0].setValue('100')
    await numbers[1].setValue('2.5')
    await flushPromises()

    expect(dialog.find('[data-testid="structured-total"]').text()).toContain('250')

    // 提交仍走既有契约 createReport({ emissionData: 结构化生成的 JSON })
    const dialogCreate = dialog.findAll('button').filter((btn) => btn.text() === '创建')[0]
    await dialogCreate.trigger('click')
    await flushPromises()

    expect(createReport).toHaveBeenCalled()
    const payload = vi.mocked(createReport).mock.calls[0][0] as { emissionData: string }
    const parsed = JSON.parse(payload.emissionData)
    expect(parsed.scope1).toEqual([{ activity_data: 100, emission_factor: 2.5 }])
    wrapper.unmount()
  })

  it('切换到高级模式：JSON 文本框可直接填写', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const createButton = wrapper.findAll('button').find((btn) => btn.text() === '创建')
    await createButton!.trigger('click')
    await flushPromises()

    // 默认结构化模式
    expect(wrapper.find('[data-testid="structured-emission"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="create-emission"]').exists()).toBe(false)

    // 切到 JSON 模式 → 出现高级文本框
    await wrapper.find('.mode-json').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="create-emission"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="structured-emission"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('排放量为 0 时不再伪造基准值，并渲染「数据不足」提示', async () => {
    vi.mocked(getAiReportAnalysis).mockResolvedValueOnce({
      report_id: 1,
      summary: '报告（1）没有可用的排放量数据',
      key_findings: [],
      reduction_suggestions: [],
      full_text: '【数据不足，无法生成诊断】',
      analysis_hash: 'a'.repeat(64),
      model_used: 'builtin-rule-engine',
      insufficient_data: true,
      insufficient_data_reason: '报告（1）没有可用的排放量数据：排放总量与 emission_details 均为空或为 0',
    })

    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      onGenerateAiAnalysis: (report: Record<string, unknown>) => Promise<void>
    }
    await vm.onGenerateAiAnalysis({ id: 1, totalEmission: 0, emissionData: '' })
    await flushPromises()

    // 绝不再把 370.5 / 250.5 / 120.0 作为兜底发给模型
    const payload = vi.mocked(getAiReportAnalysis).mock.calls[0][0]
    expect(payload.total_emission).toBe(0)
    expect(payload.scope1_emission).toBe(0)
    expect(payload.scope2_emission).toBe(0)
    expect(payload.desulf_emission).toBe(0)

    await flushPromises()
    expect(wrapper.find('[data-testid="ai-insufficient-data"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('模型调用失败时显示错误提示而不是空报告', async () => {
    vi.mocked(getAiReportAnalysis).mockRejectedValueOnce(new Error('ml down'))

    const wrapper = mountComponent()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      onGenerateAiAnalysis: (report: Record<string, unknown>) => Promise<void>
    }
    await vm.onGenerateAiAnalysis({ id: 2, totalEmission: 100, emissionData: '' })
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="ai-analysis-error"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('keeps the HISTORICAL_MOCK warning visible in the detail dialog', async () => {
    vi.mocked(getReport).mockResolvedValueOnce({
      id: 9,
      status: 5,
      chainMark: 'HISTORICAL_MOCK',
      onChainAt: '2026-10-01T10:00:00',
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    await (wrapper.vm as unknown as { viewDetail: (row: { id: number; status: number }) => Promise<void> })
      .viewDetail({ id: 9, status: 5 })
    await flushPromises()

    // el-alert stub 不继承 class，按渲染后的告警文案断言
    expect(wrapper.text()).toContain('这条存证的链上记录不可核验')
    wrapper.unmount()
  })

  it('HISTORICAL_MOCK 告警在没有 onChainAt 时也必须显示', async () => {
    vi.mocked(getReport).mockResolvedValueOnce({
      id: 10,
      status: 5,
      chainMark: 'HISTORICAL_MOCK',
      onChainAt: null,
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    await (wrapper.vm as unknown as { viewDetail: (row: { id: number; status: number }) => Promise<void> })
      .viewDetail({ id: 10, status: 5 })
    await flushPromises()

    // 外层卡片条件是 chainMark === 'HISTORICAL_MOCK' || onChainAt，
    // 只靠 onChainAt 才显示会把「链上没有、库里写着」的最危险情况藏起来
    expect(wrapper.text()).toContain('这条存证的链上记录不可核验')
    wrapper.unmount()
  })

  it('详情弹窗不渲染区块链哈希（D-02）', async () => {
    vi.mocked(getReport).mockResolvedValueOnce({
      id: 11,
      status: 5,
      blockchainTxHash: 'tx-should-never-render',
      onChainAt: '2026-10-01T10:00:00',
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    await (wrapper.vm as unknown as { viewDetail: (row: { id: number; status: number }) => Promise<void> })
      .viewDetail({ id: 11, status: 5 })
    await flushPromises()

    // 即便后端仍返回 blockchainTxHash，企业端详情也不得出现该值
    expect(wrapper.text()).not.toContain('tx-should-never-render')
    wrapper.unmount()
  })
})
