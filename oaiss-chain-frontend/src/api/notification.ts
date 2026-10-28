/**
 * ==============================================================================
 * 【API 模块】：站内信业务通知与实时消息中心 (Notification API)
 * 【对应后端】：com.oaiss.chain.controller.NotificationController (/notifications)
 * 【主要服务页面】：
 *   - 顶部导航通知铃铛: components/NotificationBell.vue (全局 layout)
 * ==============================================================================
 */
import request from './request'
import type { PageRequest } from '../types'

export interface Notification {
  id: number
  userId: number
  title: string
  content: string
  notificationType: string
  relatedEntityId: number | null
  relatedEntityType: string | null
  isRead: boolean
  createdAt: string
  updatedAt: string
}

export interface NotificationPage {
  content: Notification[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * 【业务功能】：分页查询当前登录用户的站内信通知列表（审核结果、成交确认、违约告警等）
 * 【对应前端页面】：NotificationBell.vue (顶部通知下拉弹窗)
 * 【界面触发操作】：点击页面顶部右上角【铃铛图标】展开通知抽屉或气泡列表
 * 【后端对应端点】：NotificationController -> GET /notifications
 */
export function getNotifications(params?: PageRequest): Promise<NotificationPage> {
  return request.get('/notifications', { params })
}

/**
 * 【业务功能】：获取当前用户的未读业务通知数量
 * 【对应前端页面】：NotificationBell.vue
 * 【界面触发操作】：顶部导航栏通知铃铛右上角的红色未读数角标 (Badge) 渲染
 * 【后端对应端点】：NotificationController -> GET /notifications/unread-count
 */
export function getUnreadCount(): Promise<number> {
  return request.get('/notifications/unread-count', { suppressErrorMessage: true })
}

/**
 * 【业务功能】：将指定单条业务通知标记为已读状态
 * 【对应前端页面】：NotificationBell.vue
 * 【界面触发操作】：点击某条通知消息进行查阅，或点击右侧小圆点标记已读
 * 【后端对应端点】：NotificationController -> PUT /notifications/{id}/read
 */
export function markAsRead(id: number): Promise<void> {
  return request.put(`/notifications/${id}/read`)
}

/**
 * 【业务功能】：一键将当前用户的所有未读消息标记为已读
 * 【对应前端页面】：NotificationBell.vue
 * 【界面触发操作】：点击通知弹窗右上角的【全部标为已读】按钮
 * 【后端对应端点】：NotificationController -> PUT /notifications/read-all
 */
export function markAllAsRead(): Promise<void> {
  return request.put('/notifications/read-all')
}