import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: ref('zh-CN'),
  }),
}))

vi.mock('../../api/admin', () => ({
  getUserList: vi.fn(() => Promise.resolve({ items: [], total: 0 })),
  updateUserStatus: vi.fn(() => Promise.resolve()),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import SystemUsers from '../admin/SystemUsers.vue'
import { getUserList, updateUserStatus } from '../../api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
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
    template: '<table><slot /><slot :row="data[0] || {}" :$index="0" /><slot name="append" /></table>',
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
  'el-switch': {
    template: '<button role="switch" :aria-checked="modelValue" @click="$emit(\'update:modelValue\', !modelValue)"></button>',
    props: ['modelValue', 'beforeChange', 'inlinePrompt', 'activeText', 'inactiveText'],
    emits: ['update:modelValue'],
  },
  'el-select': {
    template: '<select :value="modelValue" :data-placeholder="placeholder" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
    props: ['modelValue', 'placeholder', 'style', 'clearable'],
    emits: ['update:modelValue'],
  },
  'el-option': {
    template: '<option :value="value"><slot /></option>',
    props: ['label', 'value'],
  },
}

function mountComponent() {
  return mount(SystemUsers, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('SystemUsers.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('renders successfully', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('loads the paged backend list on mount', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getUserList).toHaveBeenCalledWith({
      page: 1,
      size: 10,
    })
    wrapper.unmount()
  })

  it('forwards selected filters to the backend instead of re-filtering the current page locally', async () => {
    vi.mocked(getUserList)
      .mockResolvedValueOnce({
        items: [
          { id: 1, username: 'alpha', userType: 1, status: 1 },
          { id: 2, username: 'beta', userType: 2, status: 0 },
        ],
        total: 2,
      } as never)
      .mockResolvedValueOnce({
        items: [{ id: 3, username: 'gamma', userType: 4, status: 1 }],
        total: 1,
      } as never)

    const wrapper = mountComponent()
    await flushPromises()

    const selects = wrapper.findAll('select')
    await selects[0].setValue('4')
    await selects[1].setValue('1')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(getUserList).toHaveBeenNthCalledWith(2, {
      page: 1,
      size: 10,
      userType: '4',
      status: '1',
    })
    expect((wrapper.vm as unknown as { userList: Array<{ id: number }> }).userList).toEqual([
      { id: 3, username: 'gamma', userType: 4, status: 1 },
    ])
    wrapper.unmount()
  })

  it('keeps server total counts authoritative after filtered responses', async () => {
    vi.mocked(getUserList)
      .mockResolvedValueOnce({
        items: [{ id: 1, username: 'alpha', userType: 1, status: 1 }],
        total: 40,
      } as never)
      .mockResolvedValueOnce({
        items: [{ id: 2, username: 'beta', userType: 2, status: 0 }],
        total: 7,
      } as never)

    const wrapper = mountComponent()
    await flushPromises()

    const selects = wrapper.findAll('select')
    await selects[1].setValue('0')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect((wrapper.vm as unknown as { total: number }).total).toBe(7)
    wrapper.unmount()
  })

  it('shows translated error when user list loading fails', async () => {
    vi.mocked(getUserList).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalledWith('systemUsers.loadFailed')
    wrapper.unmount()
  })

  it('uses neutral all-status placeholder instead of enabled', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const selects = wrapper.findAll('select')
    expect(selects[1]?.attributes('data-placeholder')).toBe('systemUsers.typeAll')

    wrapper.unmount()
  })

  it('updates user status and reloads the backend list after confirmation', async () => {
    vi.mocked(getUserList).mockResolvedValueOnce({
      items: [{ id: 5, username: 'disabled-user', userType: 1, status: 0 }],
      total: 1,
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    await (wrapper.vm as unknown as {
      handleStatusToggle: (row: { id: number; status: number }) => Promise<void>
    }).handleStatusToggle({ id: 5, status: 0 })
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(updateUserStatus).toHaveBeenCalledWith(5, 1)
    expect(ElMessage.success).toHaveBeenCalledWith('systemUsers.enableSuccess')
    expect(getUserList).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
