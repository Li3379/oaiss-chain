import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({ push: vi.fn(), back: vi.fn() })),
}))

import NotFound from '../NotFound.vue'

const stubs = {
  'el-button': {
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'loading', 'link', 'plain'],
    emits: ['click'],
  },
}

function mountNotFound() {
  return mount(NotFound, {
    global: { stubs },
    attachTo: document.body,
  })
}

describe('NotFound.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('组件正确渲染', () => {
    const wrapper = mountNotFound()
    expect(wrapper.find('.not-found').exists()).toBe(true)
    expect(wrapper.find('.content').exists()).toBe(true)
    expect(wrapper.find('.actions').exists()).toBe(true)
    wrapper.unmount()
  })

  it('显示404提示信息', () => {
    const wrapper = mountNotFound()
    expect(wrapper.find('.code').text()).toBe('404')
    expect(wrapper.find('.desc').text()).toBe('抱歉，您访问的页面不存在')
    wrapper.unmount()
  })
})
