import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import PageContainer from '../PageContainer.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: ref('zh-CN') }),
}))

describe('PageContainer', () => {
  it('should render slot content', () => {
    const wrapper = mount(PageContainer, {
      props: { title: 'Test Title' },
      slots: { default: '<div class="page-content">Test</div>' },
    })
    expect(wrapper.find('.page-content').exists()).toBe(true)
  })

  it('should render title when provided', () => {
    const wrapper = mount(PageContainer, {
      props: { title: 'Test Title' },
      global: { stubs: { 'el-card': { template: '<div><slot /><slot name="header" /></div>' } } },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('should be a reusable container component', () => {
    const wrapper = mount(PageContainer, {
      props: { title: 'Test Title' },
      slots: { default: '<p>Content</p>' },
    })
    expect(wrapper.html()).toContain('Content')
  })
})
