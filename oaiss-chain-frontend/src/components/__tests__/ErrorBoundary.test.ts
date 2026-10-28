// script-setup 组件不把绑定暴露进 vm 类型；测试断言用 any 视图访问。
// 注意：行首勿用 "(wrapper.vm as any)"——与上行 await 组合会触发 ASI 解析陷阱。
function vmOf(w: { vm: unknown }): Record<string, any> {
  return w.vm as Record<string, any>
}

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ErrorBoundary from '../ErrorBoundary.vue'

describe('ErrorBoundary', () => {
  it('renders slot content when no error', () => {
    const wrapper = mount(ErrorBoundary, {
      slots: { default: '<div class="child">Hello</div>' },
    })
    expect(wrapper.find('.child').exists()).toBe(true)
    expect(wrapper.find('.error-boundary').exists()).toBe(false)
  })

  it('shows error UI when error is captured', async () => {
    const wrapper = mount(ErrorBoundary, {
      global: { stubs: { 'el-button': true } },
    })
    const vm = wrapper.vm as unknown as { error: Error | null; info: string }
    vm.error = new Error('test error')
    vm.info = 'at component'
    await vmOf(wrapper).$nextTick()

    expect(wrapper.find('.error-boundary').exists()).toBe(true)
    expect(wrapper.find('.error-message').text()).toContain('test error')
  })

  it('clears error on retry button click', async () => {
    const wrapper = mount(ErrorBoundary, {
      global: { stubs: { 'el-button': { template: '<button @click="$emit(\'click\')">重试</button>' } } },
    })
    const vm = wrapper.vm as unknown as { error: Error | null; info: string }
    vm.error = new Error('test')
    await vmOf(wrapper).$nextTick()

    expect(wrapper.find('.error-boundary').exists()).toBe(true)

    await wrapper.find('button').trigger('click')
    expect(vm.error).toBeNull()
  })
})
