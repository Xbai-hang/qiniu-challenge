<template>
  <RouterView v-if="isPublicRoute" v-slot="{ Component, route }">
    <component :is="Component" :key="route.fullPath" />
  </RouterView>

  <div v-else class="app-shell">
    <header class="top-bar">
      <RouterLink class="brand" to="/" aria-label="返回 AI 工作台">
        <span class="brand-mark" aria-hidden="true">AI</span>
        <span>
          <strong>语音日历</strong>
          <small>AI Native Productivity OS</small>
        </span>
      </RouterLink>

      <div :class="['space-switcher', currentSpace?.type === 'organization' ? 'is-organization' : 'is-personal']">
        <span class="space-dot" aria-hidden="true"></span>
        <select
          :value="workspace.state.selectedSpaceId ?? ''"
          class="space-select"
          :disabled="workspace.state.isLoading || workspace.state.spaces.length === 0"
          aria-label="当前日历空间"
          @change="handleSpaceChange"
        >
          <option v-if="workspace.state.isLoading" value="">加载空间中</option>
          <option v-else-if="workspace.state.spaces.length === 0" value="">
            {{ workspace.state.error || '暂无可用空间' }}
          </option>
          <option v-for="space in workspace.state.spaces" :key="space.id" :value="space.id">
            {{ space.name }} · {{ spaceRoleLabel(space.role) }}
          </option>
        </select>
        <button
          type="button"
          class="space-refresh-button"
          :disabled="workspace.state.isLoading || isPublicRoute"
          aria-label="刷新空间列表"
          @click="loadSpaces"
        >
          <Refresh />
        </button>
      </div>

      <nav class="top-actions" aria-label="全局操作">
        <button type="button" class="icon-button" aria-label="全局搜索">
          <Search />
        </button>
        <button type="button" class="icon-button" aria-label="通知">
          <Bell />
        </button>
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
import { Bell, Calendar, MagicStick, Refresh, Search, Setting, SwitchButton } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { CalendarSpace } from '../api'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const workspace = useWorkspaceStore()

const isPublicRoute = computed(() => Boolean(route.meta.public))
const WORKSPACE_UPDATED_EVENT = 'organization-workspace-updated'

const currentSpace = computed(() => workspace.currentSpace.value)
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
    to: '/settings',
    label: '设置',
    icon: Setting,
  },
]

async function handleLogout() {
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

function spaceRoleLabel(role: CalendarSpace['role']) {
  const roleLabels: Record<CalendarSpace['role'], string> = {
    owner: 'Owner',
    admin: 'Admin',
    member: 'Member',
  }

  return roleLabels[role]
}

function handleWorkspaceUpdated() {
  void loadSpaces()
}

function handleSpaceChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  workspace.selectSpace(value ? Number(value) : null)
}

watch(
  () => [isPublicRoute.value, auth.state.user?.id] as const,
  () => {
    void loadSpaces()
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener(WORKSPACE_UPDATED_EVENT, handleWorkspaceUpdated)
})

onBeforeUnmount(() => {
  window.removeEventListener(WORKSPACE_UPDATED_EVENT, handleWorkspaceUpdated)
})
</script>
