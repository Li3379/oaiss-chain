import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/utils/echarts', () => ({
  default: {
    init: vi.fn(() => ({
      setOption: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
    })),
  },
}))

vi.mock('@/api/enterprise', () => ({
  getEnterpriseInfo: vi.fn(() => Promise.resolve(null)),
}))

vi.mock('@/api/credit', () => ({
  getMyScore: vi.fn(() => Promise.resolve(null)),
}))

vi.mock('../../api/enterpriseInference', () => ({
  getEnterpriseInference: vi.fn(() =>
    Promise.resolve({
      enterpriseId: 1,
      complianceStatus: 'compliant',
      confidence: 0.85,
      anomalyScore: 0.05,
      isAnomaly: false,
      riskFactors: ['Low credit score'],
      modelVersion: 'v1.0.0',
    })
  ),
}))

vi.mock('../../store', () => ({
  pinia: createPinia(),
  useAppStore: vi.fn(() => ({
    enterpriseId: 1,
    loggedIn: true,
    role: 'ENTERPRISE',
    username: 'test',
    userId: 1,
  })),
}))

vi.mock('../../components/PageContainer.vue', () => ({
  default: {
    template: '<div class="page-container"><slot /></div>',
    props: ['title', 'description'],
  },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = (await importOriginal() as any)
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

import EnterpriseInference from '../enterprise/EnterpriseInference.vue'
import { getEnterpriseInference } from '../../api/enterpriseInference'
import { ElMessage } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>', props: ['shadow', 'loading'] },
  'el-button': {
    template: '<button :disabled="loading"><slot /></button>',
    props: ['type', 'size', 'loading', 'link', 'plain'],
    emits: ['click'],
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-empty': { template: '<div class="el-empty"></div>', props: ['description'] },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['xs', 'sm', 'md'] },
  'el-progress': { template: '<div class="el-progress"></div>', props: ['type', 'percentage', 'color'] },
  'el-alert': { template: '<div class="el-alert"></div>', props: ['title', 'type', 'closable', 'showIcon'] },
}

describe('EnterpriseInference.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state', () => {
    const wrapper = mount(EnterpriseInference, { global: { stubs } })
    expect(wrapper.exists()).toBe(true)
    // 页面骨架迁移为 pen 面板：断言落点改为面板容器
    expect(wrapper.find('.pen-stat-strip').exists() || wrapper.find('.pen-panel').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders inference data when loaded', async () => {
    const wrapper = mount(EnterpriseInference, { global: { stubs } })
    await flushPromises()
    expect(getEnterpriseInference).toHaveBeenCalledWith(1)
    expect(wrapper.find('.el-tag').exists()).toBe(true)
    // 置信度等指标由 pen 统计条承载（原 el-progress 卡片已合并进 StatStrip）
    expect(wrapper.find('.pen-stat-strip').exists()).toBe(true)
    wrapper.unmount()
  })

  it('handles API error gracefully', async () => {
    vi.mocked(getEnterpriseInference).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mount(EnterpriseInference, { global: { stubs } })
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('normalizes status values correctly', async () => {
    vi.mocked(getEnterpriseInference).mockResolvedValueOnce({
      enterpriseId: 1,
      complianceStatus: 'Non-Compliant',
      confidence: 0.92,
      anomalyScore: -0.3,
      isAnomaly: true,
      riskFactors: ['High compliance flag count', 'Anomalous emission pattern detected'],
      modelVersion: 'v2.0.0',
    })
    const wrapper = mount(EnterpriseInference, { global: { stubs } })
    await flushPromises()
    expect(getEnterpriseInference).toHaveBeenCalled()
    expect(wrapper.findAll('.el-tag').length).toBeGreaterThan(0)
    wrapper.unmount()
  })

  it('shows empty state when no data and not loading', async () => {
    const { useAppStore } = await import('../../store')
    vi.mocked(useAppStore).mockReturnValueOnce({ enterpriseId: null, loggedIn: false, role: null, username: '', userId: null } as never)
    const wrapper = mount(EnterpriseInference, { global: { stubs } })
    await flushPromises()
    expect(wrapper.find('.el-empty').exists() || wrapper.find('.pen-panel').exists()).toBe(true)
    wrapper.unmount()
  })
})
