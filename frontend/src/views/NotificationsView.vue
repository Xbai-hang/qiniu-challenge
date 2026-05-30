<template>
  <main class="route-page notification-page">
    <section class="notification-hero panel">
      <div>
        <p class="eyebrow">Reminder Center</p>
        <h1>提醒中心</h1>
        <p>站内提醒、已读状态和稍后提醒都集中在这里处理。</p>
      </div>
      <div class="notification-metrics">
        <span>{{ unreadCount }}</span>
        <strong>未读提醒</strong>
      </div>
    </section>

    <section class="notification-console panel">
      <header class="notification-toolbar">
        <div class="notification-tabs" role="tablist" aria-label="通知筛选">
          <button type="button" :class="{ active: statusFilter === '' }" @click="setStatus('')">全部</button>
          <button type="button" :class="{ active: statusFilter === 'unread' }" @click="setStatus('unread')">未读</button>
          <button type="button" :class="{ active: statusFilter === 'read' }" @click="setStatus('read')">已读</button>
        </div>
        <button type="button" class="refresh-button" :disabled="isLoading" aria-label="刷新提醒" @click="load">
          <Refresh />
        </button>
      </header>

      <div v-if="isLoading" class="notification-empty large">正在同步提醒</div>
      <div v-else-if="notifications.length === 0" class="notification-empty large">当前没有提醒消息</div>
      <div v-else class="notification-list">
        <article
          v-for="notification in notifications"
          :key="notification.id"
          :class="['notification-card', { unread: notification.status === 'unread' }]"
        >
          <div class="notification-card-main">
            <span class="notification-type">{{ typeLabel(notification.type) }}</span>
            <strong>{{ notification.title }}</strong>
            <p>{{ notification.content || '日程提醒' }}</p>
            <small>{{ formatDateTime(notification.createdAt) }}</small>
          </div>

          <div class="notification-card-actions">
            <button
              type="button"
              class="line-action"
              :disabled="notification.status === 'read'"
              @click="handleRead(notification)"
            >
              标记已读
            </button>
            <button
              v-for="minutes in snoozeOptions"
              :key="minutes"
              type="button"
              class="solid-action compact"
              :disabled="!notification.reminderId"
              @click="handleSnooze(notification, minutes)"
            >
              {{ minutes }} 分钟后
            </button>
          </div>
        </article>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import type { AppNotification, NotificationStatus } from '../api'
import { useNotifications } from '../composables/useNotifications'

const notificationCenter = useNotifications()
const statusFilter = ref<NotificationStatus | ''>('')
const snoozeOptions = [5, 10, 30]

const notifications = computed(() => notificationCenter.notifications.value)
const unreadCount = computed(() => notificationCenter.unreadCount.value)
const isLoading = computed(() => notificationCenter.isLoading.value)

async function load() {
  await notificationCenter.loadNotifications({
    status: statusFilter.value || undefined,
  })
}

function setStatus(status: NotificationStatus | '') {
  statusFilter.value = status
  void load()
}

async function handleRead(notification: AppNotification) {
  await notificationCenter.readNotification(notification)
}

async function handleSnooze(notification: AppNotification, minutes: number) {
  await notificationCenter.snooze(notification, minutes)
  await load()
}

function typeLabel(type: string) {
  const labels: Record<string, string> = {
    reminder: '日程提醒',
    event_invite: '事件邀请',
    system: '系统',
    ai: 'AI',
  }
  return labels[type] ?? type
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

onMounted(() => {
  notificationCenter.connectSocket()
  void load()
})
</script>
