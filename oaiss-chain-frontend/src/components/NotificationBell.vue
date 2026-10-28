<script setup lang="ts">
/**
 * ==============================================================================
 * 【组件模块】：公共全局组件 - 站内信业务通知与实时消息中心 (NotificationBell)
 * 【挂载位置】：layout/index.vue 顶部导航栏右上角
 * 【界面可见功能】：
 *   1. 未读通知红点徽标：展示未读消息总数，实时轮询与WebSocket双通道联动
 *   2. 通知下拉气泡/弹窗：查看最新 10 条业务通知（审核通过/驳回、撮合成交、违约处罚等）
 *   3. 单条点击标为已读：点击某条消息展开详情并消除未读圆点
 *   4. 一键全部标为已读：弹窗顶部【全部已读】按钮一键清零未读红点
 * 【对应后端 Controller & API】：
 *   - NotificationController:
 *     * GET /api/v1/notifications            -> getNotifications() 分页查询通知
 *     * GET /api/v1/notifications/unread-count -> getUnreadCount() 获取未读计数
 *     * PUT /api/v1/notifications/{id}/read  -> markAsRead() 标记单条已读
 *     * PUT /api/v1/notifications/read-all   -> markAllAsRead() 全部标为已读
 * ==============================================================================
 */
import { ref, watch } from 'vue'
import { ElBadge, ElDropdown, ElDropdownMenu, ElDropdownItem, ElEmpty, ElButton, ElNotification } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../store'
import { getNotifications, markAsRead, markAllAsRead, type Notification } from '../api/notification'
import { useNotificationSocket } from '../composables/useNotificationSocket'
import { formatDateTime } from '../utils/format'

const { t } = useI18n()
const appStore = useAppStore()
const notifications = ref<Notification[]>([])
const loading = ref(false)

const { connected, unreadCount, latestNotification, fetchUnreadCount } = useNotificationSocket()

// Show browser notification when new notification arrives via WebSocket
watch(latestNotification, (notif) => {
  if (notif) {
    ElNotification({
      title: notif.title,
      message: notif.content,
      type: 'info',
      duration: 5000,
    })
  }
})

async function fetchNotifications() {
  if (!appStore.loggedIn) return
  loading.value = true
  try {
    const page = await getNotifications({ pageNum: 1, pageSize: 10 })
    const list = page?.content ?? (page as unknown as { items?: Notification[] })?.items ?? []
    notifications.value = Array.isArray(list) ? list : []
  } catch {
    notifications.value = []
  } finally {
    loading.value = false
  }
}

async function handleMarkAsRead(item: Notification) {
  if (item.isRead) return
  // 乐观更新
  item.isRead = true
  if (unreadCount.value > 0) {
    unreadCount.value--
  }
  try {
    await markAsRead(item.id)
    await fetchUnreadCount()
  } catch {
    await fetchNotifications()
  }
}

async function handleMarkAllAsRead() {
  notifications.value.forEach(n => { n.isRead = true })
  unreadCount.value = 0
  try {
    await markAllAsRead()
    await fetchUnreadCount()
  } catch {
    await fetchNotifications()
  }
}

function handleVisibleChange(visible: boolean) {
  if (visible) fetchNotifications()
}
</script>

<template>
  <el-dropdown
    trigger="click"
    placement="bottom-end"
    :hide-on-click="false"
    class="bell-dropdown"
    @visible-change="handleVisibleChange"
  >
    <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="bell-badge">
      <el-button class="bell-btn" :icon="Bell" circle :aria-label="t('notification.bell')">
        <span v-if="connected" class="ws-indicator ws-connected" :title="t('notification.wsConnected')" />
        <span v-else class="ws-indicator ws-disconnected" :title="t('notification.wsDisconnected')" />
      </el-button>
    </el-badge>
    <template #dropdown>
      <el-dropdown-menu class="notification-dropdown" role="list" :aria-label="t('notification.title')">
        <div class="notification-header">
          <div class="header-left-title">
            <span>{{ t('notification.title') }}</span>
            <span v-if="unreadCount > 0" class="unread-pill">{{ unreadCount }}</span>
          </div>
          <el-button
            v-if="unreadCount > 0"
            type="primary"
            link
            size="small"
            class="mark-all-btn"
            :aria-label="t('notification.markAllRead')"
            @click="handleMarkAllAsRead"
          >
            {{ t('notification.markAllRead') }}
          </el-button>
        </div>
        <div v-if="loading" class="notification-loading">{{ t('common.loading') }}</div>
        <template v-else-if="notifications.length > 0">
          <el-dropdown-item
            v-for="item in notifications"
            :key="item.id"
            role="listitem"
            :class="{ 'is-unread': !item.isRead }"
            @click="handleMarkAsRead(item)"
          >
            <div class="notification-item" :title="item.content">
              <div class="item-head">
                <span class="notification-title">{{ item.title }}</span>
                <span v-if="item.createdAt" class="notification-time">{{ formatDateTime(item.createdAt) }}</span>
              </div>
              <div class="notification-content">{{ item.content }}</div>
            </div>
          </el-dropdown-item>
        </template>
        <el-empty v-else :description="t('notification.empty')" :image-size="60" />
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.notification-dropdown {
  min-width: 340px;
  max-width: 400px;
  max-height: 440px;
  overflow-y: auto;
}
.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  font-weight: 600;
  border-bottom: 1px solid var(--border-color, #e4e7ed);
}
.header-left-title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.unread-pill {
  font-size: 11px;
  background-color: var(--el-color-danger, #f56c6c);
  color: #fff;
  padding: 1px 6px;
  border-radius: 10px;
  font-weight: 500;
  line-height: 14px;
}
.notification-loading {
  padding: 20px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.notification-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 4px 0;
}
.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.notification-title {
  font-weight: 600;
  font-size: var(--text-sm, 13px);
  color: var(--text-primary, #303133);
}
.notification-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #a8abb2);
  white-space: nowrap;
}
.notification-content {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-secondary, #606266);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.is-unread {
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.ws-indicator {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.ws-connected {
  background: var(--el-color-success, #67c23a);
}
.ws-disconnected {
  background: var(--el-color-info-light-3, #c8c9cc);
}
</style>
