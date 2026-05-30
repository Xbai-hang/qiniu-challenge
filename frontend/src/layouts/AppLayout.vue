<template>
  <RouterView v-if="isPublicRoute" v-slot="{ Component, route }">
    <component :is="Component" :key="route.fullPath" />
  </RouterView>

  <div v-else class="app-shell">
    <header class="top-bar">
      <RouterLink class="brand" to="/" aria-label="返回 AI 工作台">
        <span class="brand-mark" aria-hidden="true">
          <span class="brand-wave"></span>
        </span>
        <span>
          <strong>语历</strong>
          <small>Vocalendar</small>
        </span>
      </RouterLink>

      <nav class="top-actions" aria-label="全局操作">
        <button type="button" class="icon-button" aria-label="全局搜索">
          <Search />
        </button>
        <button
          type="button"
          :class="['icon-button', 'notification-trigger', { active: isNotificationPanelOpen }]"
          aria-label="通知"
          @click="toggleNotificationPanel"
        >
          <Bell />
          <span v-if="notificationCenter.unreadCount.value > 0" class="notification-dot">
            {{ compactCount(notificationCenter.unreadCount.value) }}
          </span>
        </button>
        <div v-if="isNotificationPanelOpen" class="notification-popover">
          <header>
            <div>
              <p class="eyebrow">Reminder Center</p>
              <strong>提醒中心</strong>
            </div>
            <span>{{ notificationCenter.unreadCount.value }} 未读</span>
          </header>

          <div v-if="notificationCenter.isLoading.value" class="notification-empty">正在同步提醒</div>
          <div v-else-if="notificationCenter.notifications.value.length === 0" class="notification-empty">
            暂无站内提醒
          </div>
          <div v-else class="notification-popover-list">
            <button
              v-for="notification in notificationCenter.notifications.value.slice(0, 5)"
              :key="notification.id"
              type="button"
              :class="['notification-mini-card', { unread: notification.status === 'unread' }]"
              @click="handleNotificationRead(notification)"
            >
              <strong>{{ notification.title }}</strong>
              <span>{{ notification.content || '日程提醒' }}</span>
              <small>{{ formatNotificationTime(notification.createdAt) }}</small>
            </button>
          </div>

          <RouterLink class="notification-more" to="/notifications" @click="isNotificationPanelOpen = false">
            查看全部提醒
          </RouterLink>
        </div>
        <button type="button" class="user-button" :disabled="auth.state.isLoading" @click="handleLogout">
          <span class="user-avatar" aria-hidden="true">{{ userInitial }}</span>
          <span class="user-name">{{ auth.state.user?.displayName || auth.state.user?.username }}</span>
          <SwitchButton />
        </button>
      </nav>
    </header>

    <aside class="side-nav" aria-label="主导航">
      <p class="nav-label">Workspace</p>
      <RouterLink v-for="item in navigationItems" :key="item.to" class="nav-item" :to="item.to">
        <span class="nav-icon" aria-hidden="true">
          <component :is="item.icon" />
        </span>
        <span>{{ item.label }}</span>
      </RouterLink>
    </aside>

    <RouterView v-slot="{ Component, route }">
      <component :is="Component" :key="String(route.name ?? route.path)" />
    </RouterView>
  </div>
</template>

<script setup lang="ts">
import { Bell, Calendar, Document, MagicStick, Search, Setting, SwitchButton } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { AppNotification } from '../api'
import { useNotifications } from '../composables/useNotifications'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const workspace = useWorkspaceStore()
const notificationCenter = useNotifications()

const isPublicRoute = computed(() => Boolean(route.meta.public))
const WORKSPACE_UPDATED_EVENT = 'organization-workspace-updated'
const isNotificationPanelOpen = ref(false)

const userInitial = computed(() => {
  const name = auth.state.user?.displayName || auth.state.user?.username || 'U'
  return name.slice(0, 1).toUpperCase()
})

const navigationItems = [
  {
    to: '/',
    label: 'AI 工作台',
    icon: MagicStick,
  },
  {
    to: '/calendar',
    label: '日历',
    icon: Calendar,
  },
  {
    to: '/operation-logs',
    label: '操作日志记录',
    icon: Document,
  },
  {
    to: '/notifications',
    label: '提醒中心',
    icon: Bell,
  },
  {
    to: '/settings',
    label: '设置',
    icon: Setting,
  },
]

async function handleLogout() {
  notificationCenter.disconnectSocket()
  await auth.signOut()
  workspace.resetWorkspace()
  await router.push({ name: 'login' })
}

async function loadSpaces() {
  if (isPublicRoute.value || !auth.state.user) {
    workspace.resetWorkspace()
    return
  }

  await workspace.loadSpaces({ force: true })
}

function handleWorkspaceUpdated() {
  void loadSpaces()
}

function toggleNotificationPanel() {
  isNotificationPanelOpen.value = !isNotificationPanelOpen.value
  if (isNotificationPanelOpen.value) {
    void notificationCenter.loadNotifications()
  }
}

async function handleNotificationRead(notification: AppNotification) {
  await notificationCenter.readNotification(notification)
}

function compactCount(count: number) {
  return count > 99 ? '99+' : String(count)
}

function formatNotificationTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

watch(
  () => [isPublicRoute.value, auth.state.user?.id] as const,
  () => {
    void loadSpaces()
    if (!isPublicRoute.value && auth.state.user) {
      void notificationCenter.loadNotifications()
      notificationCenter.connectSocket()
    }
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener(WORKSPACE_UPDATED_EVENT, handleWorkspaceUpdated)
  if (!isPublicRoute.value && auth.state.user) {
    notificationCenter.connectSocket()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener(WORKSPACE_UPDATED_EVENT, handleWorkspaceUpdated)
  notificationCenter.disconnectSocket()
})
</script>
