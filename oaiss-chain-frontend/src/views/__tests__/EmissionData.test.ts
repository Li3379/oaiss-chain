// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
// 注意：行首勿用 "(wrapper.vm as any)"——与上行 await 组合会触发 ASI 解析陷阱。
function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/emission', () => ({
  getMyRating: vi.fn(),
  getIndustryRankings: vi.fn(),
  predictEmission: vi.fn(),
}))

vi.mock('../../api/enterprise', () => ({
  getEnterpriseInfo: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import EmissionData from '../enterprise/EmissionData.vue'
import { getEnterpriseInfo } from '../../api/enterprise'
import { getIndustryRankings, getMyRating, predictEmission } from '../../api/emission'
import { ElMessage } from 'element-plus'

const ratingRows = [
  {
    id: 1,
    enterpriseId: 1001,
    ratingYear: '2026',
    totalEmission: 100,
    emissionIntensity: 1.2,
    ratingLevel: 'A',
    ratingScore: 95,
    percentileRank: 10,
    reductionRatio: 8,
    ratedBy: 3,
    remark: 'steady decline',
    createdAt: '2026-05-01T00:00:00',
    updatedAt: '2026-05-01T00:00:00',
    deleted: false,
  },
]

const rankingRows = [
  {
    ...ratingRows[0],
    id: 2,
    enterpriseId: 2002,
    enterpriseName: 'Ranked Corp',
  },
]

const predictionResponse = {
  enterpriseId: 1001,
  confidence: 91,
  message: 'Prediction generated successfully',
  generatedAt: '2026-05-31T09:30:00',
  predictions: [
    { period: '2026-06', predictedEmission: 88 },
    { period: '2026-07', predictedEmission: 84 },
  ],
}

const stubs = {
  PageContainer: { template: '<div><slot /></div>', props: ['title', 'description'] },
  'el-card': { template: '<div class="el-card"><slot /></div>' },
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
    template: '<td><slot :row="row" :$index="0" /></td>',
    props: ['prop', 'label', 'minWidth', 'width', 'fixed', 'showOverflowTooltip'],
    data() {
      return { row: rankingRows[0] }
    },
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type'] },
  'el-tabs': {
    template: '<div class="el-tabs"><slot /></div>',
    props: ['modelValue'],
    emits: ['update:modelValue'],
  },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name', 'disabled'] },
  'el-option': {
    template: '<option :value="value"><slot /></option>',
    props: ['label', 'value'],
  },
  'el-date-picker': {
    template: '<input class="date-picker" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @change="$emit(\'change\', $event.target.value)" />',
    props: ['modelValue', 'type', 'valueFormat', 'placeholder'],
    emits: ['update:modelValue', 'change'],
  },
  'el-input-number': {
    template: '<input role="spinbutton" :value="modelValue ?? \'\'" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
    props: ['modelValue', 'min', 'max'],
    emits: ['update:modelValue'],
  },
  'el-alert': { template: '<div class="el-alert">{{ title }}</div>', props: ['title', 'type', 'closable', 'showIcon'] },
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['title', 'column', 'border'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label', 'span'] },
}

function mountComponent() {
  return mount(EmissionData, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('EmissionData.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.mocked(getEnterpriseInfo).mockResolvedValue({ id: 1001 } as never)
    vi.mocked(getMyRating).mockResolvedValue(ratingRows)
    vi.mocked(getIndustryRankings).mockResolvedValue(rankingRows)
    vi.mocked(predictEmission).mockResolvedValue(predictionResponse)
  })

  it('loads ratings, rankings, and enterprise context on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getEnterpriseInfo).toHaveBeenCalledTimes(1)
    expect(getMyRating).toHaveBeenCalledTimes(1)
    expect(getIndustryRankings).toHaveBeenCalledWith(new Date().getFullYear())
    expect(vmOf(wrapper).predictForm.enterpriseId).toBe('1001')
    expect(vmOf(wrapper).ratings).toEqual(ratingRows)
    expect(vmOf(wrapper).rankings).toEqual(rankingRows)

    wrapper.unmount()
  })

  it('reloads rankings from the backend when the year changes', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).rankingsYear = '2025'
    await vmOf(wrapper).loadRankings()
    await flushPromises()

    expect(getIndustryRankings).toHaveBeenLastCalledWith(2025)
    wrapper.unmount()
  })

  it('submits a prediction request using the typed enterprise ID and months', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).predictForm.predictMonths = 9
    await vmOf(wrapper).onPredict()
    await flushPromises()

    expect(predictEmission).toHaveBeenCalledWith({
      enterpriseId: 1001,
      predictMonths: 9,
    })
    expect(vmOf(wrapper).predictResult).toEqual(predictionResponse)
    expect(ElMessage.success).toHaveBeenCalledWith('预测完成')

    wrapper.unmount()
  })

  it('guards prediction when the enterprise ID is invalid', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    vmOf(wrapper).predictForm.enterpriseId = ''
    await vmOf(wrapper).onPredict()

    expect(predictEmission).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('企业ID不能为空')
    wrapper.unmount()
  })

  it('shows an error when rating loading fails', async () => {
    vi.mocked(getMyRating).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('加载评级数据失败')
    wrapper.unmount()
  })
})
