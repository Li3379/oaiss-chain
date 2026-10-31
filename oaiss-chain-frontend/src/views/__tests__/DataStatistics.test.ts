import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: ref('zh-CN'),
  }),
}))

vi.mock('../../api/admin', () => ({
  getStatistics: vi.fn(() => Promise.resolve({
    totalUsers: 10,
    enterpriseCount: 4,
    reviewerCount: 3,
    thirdPartyCount: 2,
  })),
}))

const setOption = vi.fn()
const resize = vi.fn()
const dispose = vi.fn()

vi.mock('../../utils/echarts', () => ({
  default: {
    init: vi.fn(() => ({
      setOption,
      dispose,
      resize,
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

import DataStatistics from '../admin/DataStatistics.vue'
import { getStatistics } from '../../api/admin'
import { ElMessage } from 'element-plus'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-breadcrumb': { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
  'el-statistic': { template: '<div class="el-statistic"><slot /></div>', props: ['title', 'value'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
}

function mountComponent() {
  return mount(DataStatistics, {
    global: {
      stubs,
    },
    attachTo: document.body,
  })
}

describe('DataStatistics.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders statistics cards and chart shell', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('.stats-page').exists()).toBe(true)
    // pen 壳层迁移后，指标卡由 StatStrip 渲染，旧的 .stats-grid 已不存在
    expect(wrapper.find('.pen-stat-strip').exists()).toBe(true)
    expect(wrapper.find('.chart-box').exists()).toBe(true)
    wrapper.unmount()
  })

  it('loads admin statistics on mount and hydrates summary state', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(getStatistics).toHaveBeenCalled()
    expect((wrapper.vm as unknown as {
      statistics: { totalUsers: number; enterpriseCount: number; reviewerCount: number; thirdPartyCount: number }
    }).statistics).toEqual({
      totalUsers: 10,
      enterpriseCount: 4,
      reviewerCount: 3,
      thirdPartyCount: 2,
    })
    wrapper.unmount()
  })

  it('builds user-type distribution and renders the chart with normalized values', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(setOption).toHaveBeenCalled()
    expect((wrapper.vm as unknown as {
      getUserTypeDistribution: Array<{ value: number }>
    }).getUserTypeDistribution).toEqual([
      { name: 'dataStatistics.pieEnterprise', value: 4 },
      { name: 'dataStatistics.pieAuditor', value: 3 },
      { name: 'dataStatistics.pieThirdParty', value: 2 },
      { name: 'dataStatistics.pieAdmin', value: 1 },
    ])
    wrapper.unmount()
  })

  it('shows translated error when statistics loading fails', async () => {
    vi.mocked(getStatistics).mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalledWith('dataStatistics.loadFailed')
    wrapper.unmount()
  })
})
