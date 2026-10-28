import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import StatusTag from '../StatusTag.vue'

const stubs = {
  'el-tag': { template: '<span class="el-tag stub" :class="type"><slot /></span>', props: ['type', 'size'] },
  // el-icon 在单测环境用真实 SVG 依赖较重，桩成带类名的容器即可断言"图标存在"
  'el-icon': { template: '<i class="el-icon stub-icon"><slot /></i>', props: ['class'] },
}

function mountTag(props: Record<string, unknown>) {
  return mount(StatusTag, { props, global: { plugins: [createPinia()], stubs } })
}

/** 三重编码断言：语义色 + SVG 图标 + i18n 文案（规范：图标用 SVG，不用 emoji） */
function expectTripleEncoding(wrapper: ReturnType<typeof mountTag>, tagClass: string, label: string) {
  expect(wrapper.find(`.stub.${tagClass}`).exists()).toBe(true)
  expect(wrapper.find('.stub-icon').exists()).toBe(true)
  expect(wrapper.text()).toContain(label)
  // 图标容器对读屏隐藏（装饰性），文字承载语义
  expect(wrapper.find('.stub-icon').attributes('aria-hidden')).toBe('true')
}

describe('StatusTag 状态三重编码', () => {
  it('报告域：已上链 = success + 图标 + i18n 文案', () => {
    const wrapper = mountTag({ domain: 'report', status: 5 })
    expectTripleEncoding(wrapper, 'success', '已上链')
    wrapper.unmount()
  })

  it('报告域：审核拒绝 = danger + 图标（与后端 ReportStatusEnum 对齐）', () => {
    const wrapper = mountTag({ domain: 'report', status: 4 })
    expectTripleEncoding(wrapper, 'danger', '审核拒绝')
    wrapper.unmount()
  })

  it('交易域：已完成 = success + 图标（前端 i18n，不再直出后端中文 statusText）', () => {
    const wrapper = mountTag({ domain: 'trade', status: 2 })
    expectTripleEncoding(wrapper, 'success', '已完成')
    wrapper.unmount()
  })

  it('项目域：已终止 = 中性 info + 图标（跨页统一）', () => {
    const wrapper = mountTag({ domain: 'project', status: 5 })
    expectTripleEncoding(wrapper, 'info', '已终止')
    wrapper.unmount()
  })

  it('无域映射时使用显式文案并给出语义色兜底图标', () => {
    const wrapper = mountTag({ type: 'warning', text: '待处理' })
    expect(wrapper.text()).toContain('待处理')
    expect(wrapper.find('.stub-icon').exists()).toBe(true)
    wrapper.unmount()
  })

  it('渲染文本不包含 emoji（视觉规范：图标统一 SVG）', () => {
    const emojiPattern = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}]/u
    for (const [domain, status] of [['report', 5], ['report', 0], ['trade', 2], ['project', 6]] as const) {
      const wrapper = mountTag({ domain, status })
      expect(emojiPattern.test(wrapper.text())).toBe(false)
      wrapper.unmount()
    }
  })
})
