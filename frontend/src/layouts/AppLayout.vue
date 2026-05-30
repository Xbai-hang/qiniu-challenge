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

      <div class="space-switcher" aria-label="当前空间">
        <span class="space-dot" aria-hidden="true"></span>
        <span>个人空间</span>
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
      <component :is="Component" :key="route.fullPath" />
    </RouterView>
  </div>
</template>

<script setup lang="ts">
import { Bell, Calendar, MagicStick, Search, Setting, SwitchButton } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isPublicRoute = computed(() => Boolean(route.meta.public))
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
  await router.push({ name: 'login' })
}
</script>
