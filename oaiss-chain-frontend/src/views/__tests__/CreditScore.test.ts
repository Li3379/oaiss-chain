import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/credit', () => ({
  getMyScore: vi.fn(() => Promise.resolve({ data: { score: 85 } })),
  getScoreHistory: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  getScoreRanking: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import CreditScore from '../enterprise/CreditScore.vue'
import { getMyScore, getScoreHistory, getScoreRanking } from '../../api/credit'
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
  return mount(CreditScore, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('CreditScore.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('组件正确渲染', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('页面加载时调用API', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    expect(getMyScore).toHaveBeenCalled()
    expect(getScoreHistory).toHaveBeenCalled()
    expect(getScoreRanking).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('API调用失败显示错误消息', async () => {
    vi.mocked(getMyScore).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('组件渲染数据', async () => {
    vi.mocked(getMyScore).mockResolvedValueOnce({ score: 85 } as never)
    const wrapper = mountComponent()
    await flushPromises()
    expect(getMyScore).toHaveBeenCalled()
    wrapper.unmount()
  })
})
