import { getAccessToken, request } from './http'

export type ReminderStatus = 'pending' | 'sent' | 'read' | 'snoozed' | 'cancelled' | string

export type EventReminder = {
  id: number
  eventId: number
  calendarSpaceId: number
  userId: number
  offsetMinutes?: number | null
  triggerAt: string
  status: ReminderStatus
  snoozedFromId?: number | null
  createdBy: number
  createdAt: string
  updatedAt: string
  cancelledAt?: string | null
}

export type NotificationStatus = 'unread' | 'read' | string

export type AppNotification = {
  id: number
  userId: number
  calendarSpaceId: number
  reminderId?: number | null
  type: 'reminder' | 'system' | 'ai' | string
  title: string
  content?: string | null
  payload?: string | null
  status: NotificationStatus
  pushedAt?: string | null
  readAt?: string | null
  createdAt: string
}

export type NotificationPage = {
  items: AppNotification[]
  page: number
  size: number
  total: number
  unreadCount: number
}

export type CreateReminderPayload = {
  offsetMinutes?: number
  triggerAt?: string
  userId?: number
}

export type UpdateReminderPayload = CreateReminderPayload

export type SnoozeReminderResponse = {
  oldReminderId: number
  newReminderId: number
  triggerAt: string
  status: ReminderStatus
}

export function createEventReminder(eventId: number, payload: CreateReminderPayload) {
  return request<EventReminder>(`/events/${eventId}/reminders`, {
    method: 'POST',
    body: payload,
  })
}

export function getEventReminders(eventId: number) {
  return request<EventReminder[]>(`/events/${eventId}/reminders`)
}

export function updateReminder(reminderId: number, payload: UpdateReminderPayload) {
  return request<EventReminder>(`/reminders/${reminderId}`, {
    method: 'PATCH',
    body: payload,
  })
}

export function cancelReminder(reminderId: number) {
  return request<boolean>(`/reminders/${reminderId}/cancel`, {
    method: 'POST',
  })
}

export function snoozeReminder(reminderId: number, minutes: number) {
  return request<SnoozeReminderResponse>(`/reminders/${reminderId}/snooze`, {
    method: 'POST',
    body: { minutes },
  })
}

export function getNotifications(params: { status?: NotificationStatus; page?: number; size?: number } = {}) {
  return request<NotificationPage>('/notifications', {
    params,
    showErrorMessage: false,
  })
}

export function markNotificationRead(notificationId: number) {
  return request<AppNotification>(`/notifications/${notificationId}/read`, {
    method: 'POST',
  })
}

export function notificationWebSocketUrl() {
  const token = getAccessToken()
  if (!token) {
    return null
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const apiUrl = new URL(baseUrl, window.location.origin)
  const pathname = apiUrl.pathname.replace(/\/api\/?$/, '')
  return `${protocol}//${apiUrl.host}${pathname}/ws/notifications?token=${encodeURIComponent(token)}`
}
