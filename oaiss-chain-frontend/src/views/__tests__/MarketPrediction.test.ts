import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: ref('zh-CN') }),
}))

vi.mock('../../api/marketPrediction', () => ({
  getMarketTrend: vi.fn(() => Promise.resolve({
    forecastDates: null,
    forecastPrices: null,
    lowerBound: null,
    upperBound: null,
    trend: 'up',
    modelVersion: '1.0.0',
  })),
  getMarketPrice: vi.fn(() => Promise.resolve({
    forecastDates: ['2026-05-22'],
    forecastPrices: [100],
    lowerBound: [95],
    upperBound: [105],
    trend: 'up',
    modelVersion: '1.0.0',
  })),
  getSupplyDemand: vi.fn(() => Promise.resolve({
    forecastDates: ['2026-05-22'],
    forecastPrices: [100],
    lowerBound: [95],
    upperBound: [105],
    trend: 'flat',
    modelVersion: '1.0.0',
  })),
}))

vi.mock('../../utils/echarts', () => ({
  default: {
    init: vi.fn(() => ({
      setOption: vi.fn(),
      clear: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
    })),
  },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import MarketPrediction from '../enterprise/MarketPrediction.vue'
import { getMarketTrend } from '../../api/marketPrediction'

const stubs = {
  'el-card': { template: '<div><slot /></div>' },
  'el-row': { template: '<div><slot /></div>' },
  'el-col': { template: '<div><slot /></div>' },
  'el-select': { template: '<select><slot /></select>', props: ['modelValue'] },
  'el-option': { template: '<option><slot /></option>', props: ['label', 'value'] },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-tag': { template: '<span><slot /></span>' },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description'] },
}

function mountComponent() {
  return mount(MarketPrediction, {
    global: {
      plugins: [createPinia()],
      stubs,
    },
  })
}

describe('MarketPrediction.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('renders without crashing when forecast arrays are null', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    expect(getMarketTrend).toHaveBeenCalled()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })
})
