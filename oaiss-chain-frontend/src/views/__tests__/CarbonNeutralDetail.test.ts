import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('../../api/carbonNeutral', () => ({
  getProject: vi.fn(() => Promise.resolve({ projectName: '测试项目', status: 'DRAFT', expectedReduction: 100 })),
  updateProject: vi.fn(),
  submitProject: vi.fn(() => Promise.resolve()),
  startProject: vi.fn(() => Promise.resolve()),
  submitVerification: vi.fn(() => Promise.resolve()),
  updateMonitoring: vi.fn(() => Promise.resolve()),
  applyCertification: vi.fn(() => Promise.resolve()),
  terminateProject: vi.fn(() => Promise.resolve()),
}))

vi.mock('vue-router', () => ({
  useRoute: vi.fn(() => ({ params: { id: '1' } })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

vi.mock('../../components/PageContainer.vue', () => ({
  default: {
    template: '<div class="page-container"><slot /></div>',
    props: ['title', 'description'],
  },
}))

import CarbonNeutralDetail from '../enterprise/CarbonNeutralDetail.vue'
import { getProject } from '../../api/carbonNeutral'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>' },
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
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label'] },
  'el-tabs': { template: '<div class="el-tabs"><slot /></div>', props: ['modelValue'] },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
}

function mountDetail() {
  return mount(CarbonNeutralDetail, {
    global: { stubs },
    attachTo: document.body,
  })
}

async function flush() {
  await nextTick()
  await nextTick()
}

describe('CarbonNeutralDetail.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('组件正确渲染', async () => {
    const wrapper = mountDetail()
    await flush()
    expect(wrapper.find('.pen-page-head').exists()).toBe(true)
    expect(wrapper.find('.detail-page').exists()).toBe(true)
    wrapper.unmount()
  })

  it('根据路由参数加载数据', async () => {
    const wrapper = mountDetail()
    await flush()
    expect(getProject).toHaveBeenCalledWith(1)
    wrapper.unmount()
  })
})
