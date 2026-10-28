import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({ push: vi.fn() })),
  useRoute: vi.fn(() => ({ hash: '' })),
}))

vi.mock('../config/images', () => ({
  HERO_BG: '',
  INTRO_BG: '',
  GALLERY_IMAGES: [],
}))

vi.mock('../../api/blockchain', () => ({
  getStatus: vi.fn(() => Promise.resolve({ connected: false, mode: 'STANDBY', channel: 'mychannel', chaincode: 'carbon-chaincode' })),
}))

vi.mock('../../api/search', () => ({
  getMarketOverview: vi.fn(() => Promise.resolve({
    totalEnterprises: 5800,
    totalCarbonReports: 12400,
    totalTransactions: 98600,
    totalTradingVolume: 450000,
    networkAvailability: '99.98%',
    avgLatencyMs: 14,
    chainOnline: true,
    chainMode: 'FABRIC',
    channelName: 'carbon-channel',
    chaincode: 'carbon-chaincode',
    blockHeight: 12050,
  })),
}))

vi.mock('../../utils/auth', () => ({
  getAccessToken: vi.fn(() => null),
}))

import OfficialHome from '../OfficialHome.vue'
import { useRouter } from 'vue-router'
import { getMarketOverview } from '../../api/search'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'link', 'plain'],
    emits: ['click'],
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-tabs': { template: '<div class="el-tabs"><slot /></div>', props: ['modelValue'] },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
  'el-progress': { template: '<div class="el-progress"></div>', props: ['percentage', 'color', 'strokeWidth'] },
}

function mountOfficialHome() {
  return mount(OfficialHome, {
    global: { stubs },
    attachTo: document.body,
  })
}

describe('OfficialHome.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('组件正确渲染', () => {
    const wrapper = mountOfficialHome()
    expect(wrapper.find('.site-page').exists()).toBe(true)
    expect(wrapper.find('.hero').exists()).toBe(true)
    expect(wrapper.find('.top-nav').exists()).toBe(true)
    expect(wrapper.find('.footer').exists()).toBe(true)
    wrapper.unmount()
  })

  it('包含路由跳转功能', () => {
    const mockPush = vi.fn()
    vi.mocked(useRouter).mockReturnValue({ push: mockPush } as never)

    const wrapper = mountOfficialHome()
    // Check that buttons with jumpToRoute exist (feature cards with "点击进入" buttons)
    const buttons = wrapper.findAll('button')
    expect(buttons.length).toBeGreaterThan(0)
    wrapper.unmount()
  })

  it('组件挂载时调用 getMarketOverview 并成功渲染真实大盘指标', async () => {
    const wrapper = mountOfficialHome()
    expect(getMarketOverview).toHaveBeenCalled()

    await flushPromises()
    await nextTick()

    const text = wrapper.text()
    // 断言大盘宏观指标正确完成千分位格式化渲染
    expect(text).toContain('5,800')
    expect(text).toContain('12,400')
    expect(text).toContain('98,600')
    expect(text).toContain('450,000')
    expect(text).toContain('99.98% / 14ms')
    expect(wrapper.find('.pulse-value').text()).toBe('FABRIC · ONLINE')
    wrapper.unmount()
  })

  it('大盘返回 MOCK 模式时脉搏条动态展示 MOCK · ONLINE', async () => {
    vi.mocked(getMarketOverview).mockResolvedValueOnce({
      totalEnterprises: 5800,
      totalCarbonReports: 12400,
      totalTransactions: 98600,
      totalTradingVolume: 450000,
      networkAvailability: '99.98%',
      avgLatencyMs: 14,
      chainOnline: true,
      chainMode: 'MOCK',
      channelName: 'carbon-channel',
      chaincode: 'carbon-chaincode',
      blockHeight: 12050,
    })

    const wrapper = mountOfficialHome()
    await flushPromises()
    await nextTick()

    expect(wrapper.find('.pulse-value').text()).toBe('MOCK · ONLINE')
    wrapper.unmount()
  })

  it('getMarketOverview 接口异常时优雅降级展示兜底数据且脉搏条展示待接入', async () => {
    vi.mocked(getMarketOverview).mockRejectedValueOnce(new Error('Network error'))

    const wrapper = mountOfficialHome()
    expect(getMarketOverview).toHaveBeenCalled()

    await flushPromises()
    await nextTick()

    const text = wrapper.text()
    // 断言降级为兜底静态指标展示，不崩溃
    expect(text).toContain('3,248')
    expect(text).toContain('1,560')
    expect(text).toContain('24,892')
    expect(text).toContain('186,500')
    expect(text).toContain('99.96% / 11ms')
    expect(wrapper.find('.pulse-value').text()).toContain('待接入')
    wrapper.unmount()
  })
})
