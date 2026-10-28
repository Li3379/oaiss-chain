import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/carbonCoin', () => ({
  getMyAccount: vi.fn(),
  getTransactions: vi.fn(),
  transferCoins: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import CarbonCoin from '../enterprise/CarbonCoin.vue'
import { getMyAccount, getTransactions, transferCoins } from '../../api/carbonCoin'
import { ElMessage } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description'] },
  'el-form': {
    template: '<form @submit.prevent><slot /></form>',
    methods: {
      validate() {
        return Promise.resolve(true)
      },
      resetFields() {},
    },
  },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label', 'prop'] },
  'el-input': {
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'placeholder', 'showPassword', 'clearable', 'rows', 'maxlength'],
    emits: ['update:modelValue'],
  },
  'el-input-number': {
    template: '<input type="number" :value="modelValue ?? \'\'" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
    props: ['modelValue', 'min', 'precision', 'placeholder'],
    emits: ['update:modelValue'],
  },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading'],
    emits: ['click'],
  },
  'el-table': {
    template: '<table><slot /></table>',
    props: ['data', 'border'],
  },
  'el-table-column': {
    template: '<div class="el-table-column"><slot :row="row" :$index="0" /></div>',
    props: ['prop', 'label', 'minWidth', 'width', 'showOverflowTooltip'],
    computed: {
      row() {
        return {}
      },
    },
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-pagination': {
    template: '<div class="el-pagination"></div>',
    props: ['currentPage', 'pageSize', 'background', 'pageSizes', 'layout', 'total'],
    emits: ['size-change', 'current-change', 'update:current-page', 'update:page-size'],
  },
  'el-dialog': {
    template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose'],
    emits: ['update:modelValue'],
  },
}

function mountComponent() {
  return mount(CarbonCoin, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('CarbonCoin.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())

    vi.mocked(getMyAccount).mockResolvedValue({
      id: 1,
      userId: 8,
      balance: 100,
      totalRecharged: 140,
      totalSpent: 40,
      status: 1,
    })

    vi.mocked(getTransactions).mockResolvedValue({
      items: [
        {
          id: 101,
          txNo: 'CCT001',
          userId: 8,
          txType: 1,
          amount: 25,
          balanceBefore: 75,
          balanceAfter: 100,
          relatedQuota: null,
          relatedTradeId: null,
          counterpartId: null,
          remark: '充值',
          createdAt: '2026-05-31 12:00:00',
          updatedAt: '2026-05-31 12:00:00',
        },
      ],
      total: 1,
      page: 0,
      size: 10,
      totalPages: 1,
    })

    vi.mocked(transferCoins).mockResolvedValue({
      id: 1,
      userId: 8,
      balance: 80,
      totalRecharged: 140,
      totalSpent: 60,
      status: 1,
    })
  })

  it('loads account and paged transactions on mount', async () => {
    mountComponent()
    await flushPromises()

    expect(getMyAccount).toHaveBeenCalledTimes(1)
    expect(getTransactions).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
  })

  it('shows an error when account loading fails', async () => {
    vi.mocked(getMyAccount).mockRejectedValueOnce(new Error('network error'))

    mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('加载账户信息失败')
  })

  it('shows an error when transaction loading fails', async () => {
    vi.mocked(getTransactions).mockRejectedValueOnce(new Error('network error'))

    mountComponent()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('加载交易记录失败')
  })

  it('opens the transfer dialog from the transfer button', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('button').trigger('click')

    expect(wrapper.find('.el-dialog').exists()).toBe(true)
  })
})
