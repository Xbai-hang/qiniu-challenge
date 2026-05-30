import { ElMessage } from 'element-plus'
import { computed, readonly, ref } from 'vue'
import {
  getNotifications,
  markNotificationRead,
  notificationWebSocketUrl,
  snoozeReminder,
  type AppNotification,
  type NotificationStatus,
} from '../api'

const notifications = ref<AppNotification[]>([])
const unreadCount = ref(0)
const total = ref(0)
const isLoading = ref(false)
const socketStatus = ref<'idle' | 'connecting' | 'open' | 'closed'>('idle')
let socket: WebSocket | null = null

export function useNotifications() {
  const unreadNotifications = computed(() => notifications.value.filter((item) => item.status === 'unread'))

  async function loadNotifications(params: { status?: NotificationStatus; page?: number; size?: number } = {}) {
    isLoading.value = true
    try {
      const page = await getNotifications({ page: 1, size: 20, ...params })
      notifications.value = page.items
      unreadCount.value = page.unreadCount
      total.value = page.total
    } finally {
      isLoading.value = false
    }
  }

  async function readNotification(notification: AppNotification) {
    if (notification.status !== 'unread') {
      return notification
    }
    const updated = await markNotificationRead(notification.id)
    notifications.value = notifications.value.map((item) => (item.id === updated.id ? updated : item))
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    return updated
  }

  async function snooze(notification: AppNotification, minutes: number) {
    if (!notification.reminderId) {
      return
    }
    await snoozeReminder(notification.reminderId, minutes)
    await readNotification(notification)
    ElMessage.success(`已 ${minutes} 分钟后再次提醒`)
  }

  function connectSocket() {
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return
    }
    const url = notificationWebSocketUrl()
    if (!url) {
      return
    }
    socketStatus.value = 'connecting'
    socket = new WebSocket(url)
    socket.addEventListener('open', () => {
      socketStatus.value = 'open'
    })
    socket.addEventListener('message', (event) => {
      try {
        const payload = JSON.parse(event.data) as { title?: string }
        ElMessage({
          type: 'warning',
          message: payload.title || '你有新的日程提醒',
        })
      } catch {
        ElMessage.warning('你有新的日程提醒')
      }
      void loadNotifications()
    })
    socket.addEventListener('close', () => {
      socketStatus.value = 'closed'
      socket = null
    })
    socket.addEventListener('error', () => {
      socketStatus.value = 'closed'
    })
  }

  function disconnectSocket() {
    socket?.close()
    socket = null
    socketStatus.value = 'idle'
  }

  return {
    notifications: readonly(notifications),
    unreadNotifications,
    unreadCount: readonly(unreadCount),
    total: readonly(total),
    isLoading: readonly(isLoading),
    socketStatus: readonly(socketStatus),
    loadNotifications,
    readNotification,
    snooze,
    connectSocket,
    disconnectSocket,
  }
}
