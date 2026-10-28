/**
 * WebSocket composable for real-time notifications.
 * Uses native WebSocket with automatic reconnect and polling fallback.
 */

import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useAppStore } from '../store'
import { getUnreadCount } from '../api/notification'

interface NotificationPayload {
  title: string
  content: string
  type: string
  timestamp: string
  isRead: boolean
}

/**
 * 实时推送开关（默认关闭）。
 *
 * 现状：后端仅注册 SockJS/STOMP 端点（WebSocketConfig → registry.addEndpoint("/ws").withSockJS()），
 * 而本组合式函数使用原生 WebSocket，两者协议与路径均不匹配，连接必然失败并刷控制台错误。
 * 因此默认走 REST 轮询（未读计数 + 列表可用），实时通道待 UX-123 完成协议对接后开启：
 *   - 方案 A：前端引入 @stomp/stompjs + sockjs-client，按 STOMP 订阅 /user/queue/notifications
 *   - 方案 B：后端补一个原生 WebSocketHandler（/api/v1/ws）与现有 STOMP 端点并存
 * 开启方式：设置 VITE_NOTIFICATION_WS=true（需同时完成上述任一对齐）。
 */
const WS_ENABLED = import.meta.env.VITE_NOTIFICATION_WS === 'true'

export function useNotificationSocket() {
  const appStore = useAppStore()
  const connected = ref(false)
  const unreadCount = ref(0)
  const latestNotification = ref<NotificationPayload | null>(null)
  let ws: WebSocket | null = null
  let pollTimer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempts = 0
  const MAX_RECONNECT_ATTEMPTS = 5
  const HEARTBEAT_INTERVAL = 30000 // 30s
  const HEARTBEAT_TIMEOUT = 10000  // 10s to receive pong
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let heartbeatTimeoutTimer: ReturnType<typeof setTimeout> | null = null
  const connectionQuality = ref<'good' | 'degraded' | 'disconnected'>('disconnected')


  function getWsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/api/v1/ws`
  }

  function connect() {
    if (!appStore.loggedIn) return
    if (ws && ws.readyState === WebSocket.OPEN) return

    try {
      ws = new WebSocket(getWsUrl())

      ws.onopen = () => {
        connected.value = true
        connectionQuality.value = 'good'
        reconnectAttempts = 0
        stopPolling()
        startHeartbeat()
      }

      ws.onmessage = (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data)
          if (data.type === 'NOTIFICATION') {
            latestNotification.value = data.payload
            fetchUnreadCount()
          } else if (data.type === 'PONG') {
            handlePong()
          } else if (data.type === 'UNREAD_COUNT') {
            unreadCount.value = data.payload.unreadCount
          }
        } catch {
          // Ignore parse errors
        }
      }

      ws.onclose = () => {
        connected.value = false
        ws = null
        scheduleReconnect()
      }

      ws.onerror = () => {
        connected.value = false
        startFallbackPolling()
      }
    } catch {
      connected.value = false
      startFallbackPolling()
    }
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        try {
          ws.send(JSON.stringify({ type: 'PING' }))
          heartbeatTimeoutTimer = setTimeout(() => {
            // No pong received — connection degraded
            connectionQuality.value = 'degraded'
            if (ws) ws.close()
          }, HEARTBEAT_TIMEOUT)
        } catch {
          connectionQuality.value = 'degraded'
        }
      }
    }, HEARTBEAT_INTERVAL)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
    if (heartbeatTimeoutTimer) { clearTimeout(heartbeatTimeoutTimer); heartbeatTimeoutTimer = null }
  }

  function handlePong() {
    connectionQuality.value = 'good'
    if (heartbeatTimeoutTimer) { clearTimeout(heartbeatTimeoutTimer); heartbeatTimeoutTimer = null }
  }

  function disconnect() {
    stopHeartbeat()
    if (ws) {
      ws.close()
      ws = null
    }
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    stopPolling()
  }

  function scheduleReconnect() {
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      startFallbackPolling()
      return
    }
    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
    reconnectAttempts++
    reconnectTimer = setTimeout(connect, delay)
  }

  async function fetchUnreadCount() {
    if (!appStore.loggedIn) return
    try {
      unreadCount.value = await getUnreadCount()
    } catch {
      // non-critical
    }
  }

  function startFallbackPolling() {
    if (pollTimer) return
    fetchUnreadCount()
    pollTimer = setInterval(fetchUnreadCount, 60000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  /**
   * 启动通知通道：先保证"计数可用"，再尝试"实时"。
   * 历史问题：WS 若卡在 CONNECTING（既不 open 也不 error），轮询兜底永不启动，
   * 未读徽标恒为空。因此这里改为：立即取一次未读计数 + 先起轮询兜底，
   * WS 连接成功后由 onopen 调 stopPolling() 收敛为实时推送。
   */
  function start() {
    if (!appStore.loggedIn) return
    fetchUnreadCount()
    startFallbackPolling()
    if (WS_ENABLED) connect()
  }

  watch(() => appStore.loggedIn, (loggedIn) => {
    if (loggedIn) {
      start()
    } else {
      disconnect()
    }
  })

  onMounted(() => {
    start()
  })

  onBeforeUnmount(() => {
    disconnect()
  })

  return {
    connected,
    connectionQuality,
    unreadCount,
    latestNotification,
    fetchUnreadCount,
    disconnect,
  }
}