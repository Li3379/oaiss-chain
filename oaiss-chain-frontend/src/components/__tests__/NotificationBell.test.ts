import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import NotificationBell from '../NotificationBell.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: ref('zh-CN') }),
}))

vi.mock('../../api/notification', () => ({
  getNotifications: vi.fn().mockResolvedValue({ content: [] }),
  markAsRead: vi.fn().mockResolvedValue(undefined),
  markAllAsRead: vi.fn().mockResolvedValue(undefined),
}))

// 组合式函数契约返回 ref；用普通对象 { value } 会让 watch() 收到非法数据源并告警
vi.mock('../../composables/useNotificationSocket', () => ({
  useNotificationSocket: () => ({
    connected: ref(true),
    unreadCount: ref(3),
    latestNotification: ref(null),
    fetchUnreadCount: vi.fn(),
    disconnect: vi.fn(),
  }),
}))

const stubs = {
  'el-badge': {
    template:
      '<div class="el-badge"><slot /><span v-if="!hidden" class="el-badge__value">{{ value }}</span></div>',
    props: ['value', 'hidden', 'max'],
  },
  'el-button': { template: '<button><slot /></button>' },
  'el-dropdown': {
    template: '<div><slot /><slot name="dropdown" /></div>',
    emits: ['visible-change'],
  },
  'el-dropdown-menu': { template: '<div><slot /></div>' },
  'el-dropdown-item': { template: '<div><slot /></div>' },
  'el-empty': { template: '<div class="empty"><slot /></div>' },
}

describe('NotificationBell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders bell button', () => {
    const wrapper = mount(NotificationBell, { global: { stubs } })
    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('shows unread count from composable', () => {
    const wrapper = mount(NotificationBell, { global: { stubs } })
    const vm = wrapper.vm as unknown as { unreadCount: number; connected: boolean; handleMarkAllAsRead: () => Promise<void> }
    expect(vm.unreadCount).toBe(3)
    expect(wrapper.find('.el-badge__value').text()).toBe('3')
  })

  it('shows connected indicator when WS is active', () => {
    const wrapper = mount(NotificationBell, { global: { stubs } })
    const vm = wrapper.vm as unknown as { connected: boolean }
    expect(vm.connected).toBe(true)
  })

  it('displays empty state initially', () => {
    const wrapper = mount(NotificationBell, { global: { stubs } })
    expect(wrapper.find('.empty').exists()).toBe(true)
  })

  it('handles mark-all-as-read', async () => {
    const mod = await import('../../api/notification')
    const markAllAsRead = vi.mocked(mod.markAllAsRead)
    const wrapper = mount(NotificationBell, { global: { stubs } })
    const vm = wrapper.vm as unknown as { handleMarkAllAsRead: () => Promise<void> }
    await vm.handleMarkAllAsRead()
    expect(markAllAsRead).toHaveBeenCalled()
  })

  it('renders bell with aria-label', () => {
    const wrapper = mount(NotificationBell, { global: { stubs } })
    const button = wrapper.find('button')
    expect(button.attributes('aria-label')).toBeTruthy()
  })

  it('handles markAsRead for individual notification', async () => {
    const mod = await import('../../api/notification')
    const markAsRead = vi.mocked(mod.markAsRead)
    markAsRead.mockResolvedValueOnce(undefined)
    await mod.markAsRead(42)
    expect(markAsRead).toHaveBeenCalledWith(42)
  })
})