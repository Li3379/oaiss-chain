// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/search', () => ({
  searchReports: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  searchTrades: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
}))

vi.mock('../../store', async (importOriginal) => {
  const actual = (await importOriginal() as Record<string, unknown>)
  return {
    ...actual,
    useAppStore: () => ({ role: (globalThis as Record<string, unknown>).__testRole }),
  }
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as Record<string, unknown>)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import Search from '../Search.vue'
import { searchReports, searchTrades } from '../../api/search'
import { ElMessage } from 'element-plus'

const stubs = {
  'page-header': { template: '<div class="page-header"><slot /></div>', props: ['title', 'description', 'crumbs', 'actions'] },
  'page-hint': { template: '<div class="page-hint">{{ text }}</div>', props: ['text', 'icon'] },
  'filter-bar': { template: '<div class="filter-bar"><slot /></div>', props: ['ariaLabel'] },
  'data-panel': { template: '<div class="data-panel"><slot /><slot name="footer" /></div>', props: ['title', 'meta', 'icon'] },
  'status-tag': { template: '<span class="status-tag" />', props: ['status', 'domain'] },
  'el-tabs': { template: '<div class="el-tabs"><slot /></div>', props: ['modelValue'], emits: ['update:modelValue'] },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'placeholder', 'clearable'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'plain'],
    emits: ['click'],
  },
  'el-table': { template: '<table><slot /></table>', props: ['data'] },
  'el-table-column': {
    template: '<td><slot :row="{}" :$index="0" /></td>',
    props: ['prop', 'label', 'minWidth', 'width', 'showOverflowTooltip'],
  },
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
  'el-option': { template: '<option :value="value"><slot /></option>', props: ['label', 'value'] },
}

function mountAs(role: string) {
  ;(globalThis as Record<string, unknown>).__testRole = role
  setActivePinia(createPinia())
  return mount(Search, {
    global: { plugins: [createPinia()], stubs },
  })
}

describe('Search.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads both report and trade results on mount', async () => {
    const wrapper = mountAs('ENTERPRISE')
    await flushPromises()

    expect(searchReports).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      keyword: undefined,
      enterpriseId: undefined,
      status: undefined,
    })
    expect(searchTrades).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      tradeType: undefined,
      status: undefined,
    })
    wrapper.unmount()
  })

  it('shows the anonymization notice for enterprise and reviewer roles', async () => {
    for (const role of ['ENTERPRISE', 'REVIEWER']) {
      const wrapper = mountAs(role)
      await flushPromises()
      expect((wrapper.vm as unknown as { namesAreMasked: boolean }).namesAreMasked).toBe(true)
      expect(wrapper.find('.mask-note').text()).toContain('searchCenter.maskHint')
      wrapper.unmount()
    }
  })

  it('shows the real-name (regulator) notice for admin and third-party roles', async () => {
    for (const role of ['ADMIN', 'THIRD_PARTY']) {
      const wrapper = mountAs(role)
      await flushPromises()
      expect((wrapper.vm as unknown as { namesAreMasked: boolean }).namesAreMasked).toBe(false)
      expect(wrapper.find('.mask-note').text()).toContain('searchCenter.hintRegulator')
      wrapper.unmount()
    }
  })

  it('forwards the report filters to the backend query', async () => {
    const wrapper = mountAs('ADMIN')
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('annual')
    await inputs[1].setValue('12')
    const selects = wrapper.findAll('select')
    await selects[0].setValue('3')
    ;(wrapper.vm as unknown as { onReportSearch: () => void }).onReportSearch()
    await flushPromises()

    expect(searchReports).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      keyword: 'annual',
      enterpriseId: 12,
      status: '3',
    })
    wrapper.unmount()
  })

  it('forwards the trade filters and localizes trade types', async () => {
    const wrapper = mountAs('THIRD_PARTY')
    await flushPromises()

    const selects = wrapper.findAll('select')
    await selects[1].setValue('2')
    await selects[2].setValue('2')
    ;(wrapper.vm as unknown as { onTradeSearch: () => void }).onTradeSearch()
    await flushPromises()

    expect(searchTrades).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      tradeType: '2',
      status: '2',
    })
    expect((wrapper.vm as unknown as { tradeTypeLabel: (t: number) => string }).tradeTypeLabel(1))
      .toBe('searchCenter.tradeTypeAuction')
    expect((wrapper.vm as unknown as { tradeTypeLabel: (t: number) => string }).tradeTypeLabel(3))
      .toBe('searchCenter.tradeTypeAllocation')
    wrapper.unmount()
  })

  it('reports an error message when either query fails', async () => {
    vi.mocked(searchReports).mockRejectedValueOnce(new Error('boom'))
    const wrapper = mountAs('ADMIN')
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalledWith('searchCenter.loadReportsFailed')
    wrapper.unmount()

    vi.mocked(searchTrades).mockRejectedValueOnce(new Error('boom'))
    const wrapper2 = mountAs('ADMIN')
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalledWith('searchCenter.loadTradesFailed')
    wrapper2.unmount()
  })

  it('is a read-only page (no write API wired)', async () => {
    const wrapper = mountAs('ENTERPRISE')
    await flushPromises()

    const vm = wrapper.vm as unknown as Record<string, unknown>
    expect(vm.createP2PTrade).toBeUndefined()
    expect(vm.cancelTrade).toBeUndefined()
    expect(vm.confirmTrade).toBeUndefined()
    wrapper.unmount()
  })
})