import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../api/notification', () => ({
  getUnreadCount: vi.fn().mockResolvedValue(34),
}))

import { getUnreadCount } from '../../api/notification'
import { useNotificationSocket } from '../useNotificationSocket'
import { useAppStore } from '../../store'

const Host = defineComponent({
  setup() {
    const socket = useNotificationSocket()
    return () => h('div', String(socket.unreadCount.value))
  },
})

/**
 * 行为断言（替换原先仅断言字面量常量的 9 条空测）：
 * 覆盖 2026-09-10 修复的启动契约——先取计数、默认不建原生 WS。
 */
describe('useNotificationSocket', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('挂载即取未读计数：不依赖实时通道是否可用（未读徽标可用性回归）', async () => {
    const store = useAppStore()
    store.loggedIn = true
    const wrapper = mount(Host)
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(getUnreadCount).toHaveBeenCalled()
    expect(wrapper.text()).toContain('34')
    wrapper.unmount()
  })

  it('默认不建立原生 WebSocket：后端仅有 SockJS/STOMP 端点，原生连接必然失败（避免控制台噪音）', async () => {
    const store = useAppStore()
    store.loggedIn = true
    const original = globalThis.WebSocket
    const spy = vi.fn()
    // @ts-expect-error 测试替身
    globalThis.WebSocket = spy
    const wrapper = mount(Host)
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(spy).not.toHaveBeenCalled()
    globalThis.WebSocket = original
    wrapper.unmount()
  })

  it('未登录时不发起计数请求', async () => {
    const wrapper = mount(Host)
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(getUnreadCount).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
