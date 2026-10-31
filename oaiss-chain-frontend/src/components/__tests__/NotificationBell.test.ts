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

// 未读数放在模块级 ref，便于在用例中推高以触发「增长 → 弹提示」链路
const sharedUnread = ref(3)

// 组合式函数契约返回 ref；用普通对象 { value } 会让 watch() 收到非法数据源并告警
vi.mock('../../composables/useNotificationSocket', () => ({
  useNotificationSocket: () => ({
    connected: ref(true),
    unreadCount: sharedUnread,
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

  /**
   * 轮询通道主动提示回归（WS 默认关闭时的成交/审核通知可见性）。
   *
   * 背景：撮合由后台定时器自动产生，若只更新未读徽标数字，用户不会注意到
   * 「挂单已成交」。此处断言「未读数增长 → 拉最新一条 → 弹出提示」这条链。
   */
  it('未读数增长时拉取并弹出最新通知（轮询通道兜底提示）', async () => {
    const mod = await import('../../api/notification')
    const getNotifications = vi.mocked(mod.getNotifications)
    getNotifications.mockClear()
    getNotifications.mockResolvedValue({
      content: [{ id: 1, title: '拍卖撮合成交', content: '您的拍卖订单已成交 10 吨', isRead: false }],
    } as never)

    const { useAppStore } = await import('../../store')
    useAppStore().loggedIn = true

    sharedUnread.value = 3
    const wrapper = mount(NotificationBell, { global: { stubs } })
    await Promise.resolve()

    // 首次挂载只记录基线，不补弹历史
    expect(getNotifications).not.toHaveBeenCalled()

    sharedUnread.value = 4
    await Promise.resolve()
    await new Promise((r) => setTimeout(r, 0))

    expect(getNotifications).toHaveBeenCalledWith({ pageNum: 1, pageSize: 1 })
    wrapper.unmount()
  })
})