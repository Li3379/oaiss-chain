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

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import Monitor from '../third-party/Monitor.vue'
import { getCarbonReports, getOrgInfo, getStatistics, updateContact } from '../../api/thirdParty'
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
})
