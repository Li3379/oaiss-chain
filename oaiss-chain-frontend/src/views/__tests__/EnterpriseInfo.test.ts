// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
// 注意：行首勿用 "(wrapper.vm as any)"——与上行 await 组合会触发 ASI 解析陷阱。
function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/enterprise', () => ({
  getEnterpriseInfo: vi.fn(() =>
    Promise.resolve({
      id: 1,
      userId: 10,
      enterpriseName: 'Test Corp',
      creditCode: '91110000MA01XXXXX',
      address: '123 Test Street',
      contactPerson: 'Alice',
      contactPhone: '13800000000',
      industry: 'Manufacturing',
      scale: 'Large',
      carbonQuota: 5000,
      carbonUsed: 2000,
      carbonTradable: 3000,
      licenseUrl: '',
      certStatus: 1,
      createdAt: '2025-01-15T08:00:00Z',
    })
  ),
  getQuotaInfo: vi.fn(() =>
    Promise.resolve({
      totalQuota: 5000,
      usedQuota: 2000,
      tradableQuota: 3000,
      enterpriseName: 'Test Corp',
      remainingQuota: 3000,
      usageRate: '40.00',
    })
  ),
  updateContact: vi.fn(() => Promise.resolve()),
}))

vi.mock('../../api/carbonCoin', () => ({
  getMyAccount: vi.fn(() => Promise.resolve({ balance: 1500.5 })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

import EnterpriseInfo from '../enterprise/EnterpriseInfo.vue'
import { getEnterpriseInfo, getQuotaInfo, updateContact } from '../../api/enterprise'
import { getMyAccount } from '../../api/carbonCoin'
import { ElMessage } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>', props: ['shadow', 'loading'] },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type'] },
  'el-empty': { template: '<div class="el-empty"></div>', props: ['description'] },
  'el-button': {
    template: '<button :disabled="loading"><slot /></button>',
    props: ['type', 'size', 'loading', 'link', 'plain'],
    emits: ['click'],
  },
  'el-form': {
    template: '<form @submit.prevent><slot /></form>',
    methods: {
      validate() { return Promise.resolve(true) },
      resetFields() {},
    },
  },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label', 'prop'] },
  'el-input': {
    template: '<input />',
    props: ['modelValue', 'type', 'placeholder', 'showPassword', 'clearable', 'rows'],
    emits: ['update:modelValue'],
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose'],
    emits: ['update:modelValue'],
  },
  'el-row': { template: '<div><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div><slot /></div>', props: ['xs', 'sm', 'md'] },
  'el-statistic': { template: '<div></div>' },
  'el-progress': { template: '<div></div>' },
}

describe('EnterpriseInfo.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state', () => {
    const wrapper = mount(EnterpriseInfo, { global: { stubs } })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.find('.el-card').exists()).toBe(true)
    wrapper.unmount()
  })

  it('displays enterprise info when loaded', async () => {
    const wrapper = mount(EnterpriseInfo, { global: { stubs } })
    await flushPromises()
    expect(getEnterpriseInfo).toHaveBeenCalled()
    expect(getMyAccount).toHaveBeenCalled()
    expect(wrapper.find('.el-descriptions').exists()).toBe(true)
    expect(wrapper.text()).toContain('Test Corp')
    wrapper.unmount()
  })

  it('displays quota info', async () => {
    const wrapper = mount(EnterpriseInfo, { global: { stubs } })
    await flushPromises()
    expect(getQuotaInfo).toHaveBeenCalled()
    const descriptions = wrapper.findAll('.el-descriptions')
    expect(descriptions.length).toBeGreaterThanOrEqual(2)
    wrapper.unmount()
  })

  it('handles update contact form submission', async () => {
    const wrapper = mount(EnterpriseInfo, { global: { stubs } })
    await flushPromises()

    vi.mocked(updateContact).mockResolvedValueOnce(undefined)
    await vmOf(wrapper).submitContact()
    await flushPromises()

    expect(updateContact).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('handles API error gracefully', async () => {
    vi.mocked(getEnterpriseInfo).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mount(EnterpriseInfo, { global: { stubs } })
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })
})
