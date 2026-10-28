import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('../../api/carbon', () => ({
  getReportList: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import SystemCarbon from '../admin/SystemCarbon.vue'
import { getReportList } from '../../api/carbon'
import { ElMessage } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
  'el-form': { template: '<form @submit.prevent><slot /></form>' },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label', 'prop'] },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'placeholder', 'clearable'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'loading'],
    emits: ['click'],
  },
  'el-table': {
    template: '<table><slot /></table>',
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
}

function mountComponent() {
  return mount(SystemCarbon, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('SystemCarbon.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('renders the page shell', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('loads paged report data on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getReportList).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      keyword: undefined,
    })
    wrapper.unmount()
  })

  it('forwards keyword searches to the backend instead of locally re-filtering the current page', async () => {
    vi.mocked(getReportList)
      .mockResolvedValueOnce({
        items: [
          { id: 1, reportNo: 'RPT-001', title: 'Alpha', enterpriseName: 'A Corp' } as never,
          { id: 2, reportNo: 'RPT-002', title: 'Beta', enterpriseName: 'B Corp' } as never,
        ],
        total: 2,
        page: 1,
        size: 10,
        totalPages: 1,
      })
      .mockResolvedValueOnce({
        items: [{ id: 3, reportNo: 'RPT-003', title: 'Gamma', enterpriseName: 'Gamma Corp' } as never],
        total: 1,
        page: 1,
        size: 10,
        totalPages: 1,
      })

    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('input').setValue('gamma')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(getReportList).toHaveBeenNthCalledWith(2, {
      pageNum: 1,
      pageSize: 10,
      keyword: 'gamma',
    })
    expect((wrapper.vm as unknown as { tableData: Array<{ id: number }> }).tableData).toEqual([
      { id: 3, reportNo: 'RPT-003', title: 'Gamma', enterpriseName: 'Gamma Corp' },
    ])
    wrapper.unmount()
  })

  it('keeps server total counts authoritative after a search response returns fewer rows', async () => {
    vi.mocked(getReportList)
      .mockResolvedValueOnce({
        items: [{ id: 1, reportNo: 'RPT-001', title: 'Alpha', enterpriseName: 'A Corp' } as never],
        total: 27,
        page: 1,
        size: 10,
        totalPages: 3,
      })
      .mockResolvedValueOnce({
        items: [{ id: 2, reportNo: 'RPT-002', title: 'Filtered', enterpriseName: 'B Corp' } as never],
        total: 11,
        page: 1,
        size: 10,
        totalPages: 2,
      })

    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('input').setValue('filtered')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect((wrapper.vm as unknown as { total: number }).total).toBe(11)
    wrapper.unmount()
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(getReportList).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('systemCarbon.loadFailed')
    wrapper.unmount()
  })
})
