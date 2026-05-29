<template>
  <main class="workspace-grid">
    <section class="panel assistant-panel">
      <p class="eyebrow">AI Agent Console</p>
      <h1>语音日历 AI Native</h1>
      <p class="summary">
        以语音和自然语言驱动日程创建、查询、调整与提醒，后续页面会在这个应用壳内逐步接入完整工作流。
      </p>

      <div class="voice-orb" aria-hidden="true"></div>

      <div class="command-box">
        <span>今天下午有什么安排？</span>
        <button type="button">发送</button>
      </div>
    </section>

    <section class="panel workspace-panel">
      <div class="workspace-header">
        <div>
          <p class="eyebrow">Workspace</p>
          <h2>今日日程骨架</h2>
        </div>
        <span :class="['status', healthStatusClass]">{{ healthStatusLabel }}</span>
      </div>

      <div class="calendar-grid" aria-label="Calendar skeleton">
        <div v-for="day in days" :key="day" class="calendar-cell">
          <span>{{ day }}</span>
        </div>
      </div>
    </section>

    <section class="panel insight-panel">
      <div class="health-heading">
        <div>
          <p class="eyebrow">Backend Health</p>
          <h2>/api/health</h2>
        </div>
        <button type="button" class="refresh-button" :disabled="isHealthLoading" @click="loadHealthStatus">
          <Refresh />
        </button>
      </div>

      <div :class="['health-card', healthStatusClass]">
        <span class="health-dot" aria-hidden="true"></span>
        <div>
          <strong>{{ healthStatusTitle }}</strong>
          <p>{{ healthStatusDescription }}</p>
        </div>
      </div>

      <dl v-if="health" class="health-details">
        <div>
          <dt>Version</dt>
          <dd>{{ health.version }}</dd>
        </div>
        <div>
          <dt>Server Time</dt>
          <dd>{{ health.time }}</dd>
        </div>
      </dl>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { getHealthStatus, type HealthStatus } from '../api'

const days = Array.from({ length: 14 }, (_, index) => index + 1)

const health = ref<HealthStatus | null>(null)
const healthError = ref('')
const isHealthLoading = ref(false)

const healthStatusClass = computed(() => {
  if (isHealthLoading.value) {
    return 'is-loading'
  }

  if (health.value?.status === 'UP') {
    return 'is-online'
  }

  return 'is-offline'
})

const healthStatusLabel = computed(() => {
  if (isHealthLoading.value) {
    return 'Checking'
  }

  return health.value?.status === 'UP' ? 'API Online' : 'API Offline'
})

const healthStatusTitle = computed(() => {
  if (isHealthLoading.value) {
    return '正在检查后端服务'
  }

  return health.value?.status === 'UP' ? '后端服务运行正常' : '后端服务未连接'
})

const healthStatusDescription = computed(() => {
  if (isHealthLoading.value) {
    return '前端正在请求健康检查接口。'
  }

  if (health.value?.status === 'UP') {
    return '前端 API 客户端已成功读取服务状态。'
  }

  return healthError.value || '请确认后端已启动并监听 8080 端口。'
})

async function loadHealthStatus() {
  isHealthLoading.value = true
  healthError.value = ''

  try {
    health.value = await getHealthStatus({ showErrorMessage: false })
  } catch (error) {
    health.value = null
    healthError.value = error instanceof Error ? error.message : '健康检查请求失败'
  } finally {
    isHealthLoading.value = false
  }
}

onMounted(() => {
  void loadHealthStatus()
})
</script>
