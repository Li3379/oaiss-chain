import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: { count?: number }) => {
      if (key === 'common.total') return `common.total:${params?.count ?? ''}`
      return key
    },
    locale: ref('zh-CN'),
  }),
}))

vi.mock('../../api/admin', () => ({
  getConfig: vi.fn(() => Promise.resolve({
    systemName: 'OAISS CHAIN',
    version: '1.0.0',
    maxUploadSize: '10MB',
    sessionTimeout: 3600,
    enableCaptcha: true,
    enableBlockChain: false,
  })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  }
})

import SystemConfig from '../admin/SystemConfig.vue'
import { getConfig } from '../../api/admin'
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
    template: '<table><slot /><slot :row="data[0] || {}" :$index="0" /><slot name="append" /></table>',
    props: ['data', 'border', 'emptyText'],
  },
  'el-table-column': {
    template: '<td><slot :row="row ?? {}" :$index="0" /></td>',
    props: ['prop', 'label', 'minWidth', 'width', 'fixed', 'showOverflowTooltip', 'row'],
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

function mountSystemConfig() {
  return mount(SystemConfig, {
    global: {
      stubs,
    },
    attachTo: document.body,
  })
}

describe('SystemConfig.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders shell and loads configuration on mount', async () => {
    const wrapper = mountSystemConfig()
    await flushPromises()

    expect(wrapper.find('.config-page').exists()).toBe(true)
    expect(getConfig).toHaveBeenCalled()
    expect((wrapper.vm as unknown as { configList: Array<{ id: string }> }).configList).toHaveLength(6)
    wrapper.unmount()
  })

  it('normalizes boolean and numeric config values into display rows', async () => {
    const wrapper = mountSystemConfig()
    await flushPromises()

    expect((wrapper.vm as unknown as { configList: Array<{ id: string; value: string }> }).configList).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ id: 'enableCaptcha', value: 'true' }),
        expect.objectContaining({ id: 'enableBlockChain', value: 'false' }),
        expect.objectContaining({ id: 'sessionTimeout', value: '3600' }),
      ]),
    )
    wrapper.unmount()
  })

  it('filters config rows by description and name', async () => {
    const wrapper = mountSystemConfig()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      searchForm: { description: string; name: string }
      filteredData: Array<{ id: string }>
      onQuery: () => void
    }

    vm.searchForm.name = 'version'
    vm.onQuery()
    await flushPromises()

    expect(vm.filteredData).toEqual([
      expect.objectContaining({ id: 'version' }),
    ])
    wrapper.unmount()
  })

  it('shows translated load failure when config query fails', async () => {
    vi.mocked(getConfig).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountSystemConfig()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('systemConfig.loadFailed')
    wrapper.unmount()
  })
})
