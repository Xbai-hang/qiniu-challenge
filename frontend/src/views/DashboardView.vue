<template>
  <main class="ai-home">
    <section class="ai-home-command panel">
      <div class="command-copy">
        <p class="eyebrow">AI Agent Console</p>
        <h1>今天要安排什么？</h1>
        <p>用语音或文本驱动日历。手动视图、冲突提示和企业字段会在日历工作台里同步展示。</p>
      </div>

      <button type="button" class="home-voice-button" aria-label="开始语音输入">
        <Microphone />
      </button>

      <div class="home-command-input">
        <input v-model="prompt" type="text" aria-label="AI 指令输入" />
        <RouterLink class="home-send-button" to="/calendar">
          <Promotion />
          <span>打开日历</span>
        </RouterLink>
      </div>

      <div class="home-tool-grid" aria-label="快捷工具">
        <RouterLink v-for="tool in tools" :key="tool.title" class="home-tool-card" :to="tool.to">
          <component :is="tool.icon" />
          <span>{{ tool.title }}</span>
          <small>{{ tool.description }}</small>
        </RouterLink>
      </div>
    </section>

    <section class="ai-home-workspace panel">
      <header class="home-section-header">
        <div>
          <p class="eyebrow">Workspace Flow</p>
          <h2>AI 执行路径</h2>
        </div>
        <span :class="['status', healthStatusClass]">{{ healthStatusLabel }}</span>
      </header>

      <div class="home-flow-board">
        <article v-for="step in flowSteps" :key="step.title" class="home-flow-card">
          <span :class="['home-flow-index', step.tone]">{{ step.index }}</span>
          <div>
            <strong>{{ step.title }}</strong>
            <p>{{ step.description }}</p>
          </div>
        </article>
      </div>

      <div class="home-preview-shell">
        <div class="home-preview-header">
          <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
        </div>
        <div class="home-preview-grid" aria-label="日历预览">
          <div v-for="cell in previewCells" :key="cell.day" :class="['home-preview-cell', cell.active ? 'active' : '']">
            <strong>{{ cell.day }}</strong>
            <span v-if="cell.label">{{ cell.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <aside class="ai-home-insight panel">
      <header class="home-section-header">
        <div>
          <p class="eyebrow">Live Context</p>
          <h2>当前上下文</h2>
        </div>
        <button type="button" class="refresh-button" :disabled="isHealthLoading" @click="loadHealthStatus">
          <Refresh />
        </button>
      </header>

      <section class="home-context-card">
        <span class="space-dot" aria-hidden="true"></span>
        <div>
          <strong>{{ currentSpace?.name || '等待空间列表' }}</strong>
          <p>{{ currentSpaceDescription }}</p>
        </div>
      </section>

      <section :class="['health-card', healthStatusClass]">
        <span class="health-dot" aria-hidden="true"></span>
        <div>
          <strong>{{ healthStatusTitle }}</strong>
          <p>{{ healthStatusDescription }}</p>
        </div>
      </section>

      <dl v-if="health" class="health-details compact">
        <div>
          <dt>Version</dt>
          <dd>{{ health.version }}</dd>
        </div>
        <div>
          <dt>Server Time</dt>
          <dd>{{ health.time }}</dd>
        </div>
      </dl>

      <section class="home-insight-list">
        <div class="mini-section-heading">
          <strong>建议动作</strong>
          <span>3</span>
        </div>
        <RouterLink class="home-suggestion" to="/calendar">
          <Calendar />
          <span>进入月历查看本月日程</span>
        </RouterLink>
        <RouterLink class="home-suggestion" to="/operation-logs">
          <Document />
          <span>查看最近手动写操作</span>
        </RouterLink>
        <RouterLink class="home-suggestion" to="/settings">
          <Setting />
          <span>检查模型与语音配置</span>
        </RouterLink>
      </section>
    </aside>
  </main>
</template>

<script setup lang="ts">
import { Calendar, Document, Microphone, Promotion, Refresh, Setting } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getHealthStatus, type HealthStatus } from '../api'
import { useWorkspaceStore } from '../stores/workspace'

const workspace = useWorkspaceStore()
const prompt = ref('明天下午三点安排项目复盘，并提前十五分钟提醒我')
const health = ref<HealthStatus | null>(null)
const healthError = ref('')
const isHealthLoading = ref(false)

const tools = [
  {
    title: '日历工作台',
    description: '月历、列表、表格、甘特',
    to: '/calendar',
    icon: Calendar,
  },
  {
    title: 'AI 操作日志',
    description: '查看工具调用和手动写操作',
    to: '/operation-logs',
    icon: Document,
  },
  {
    title: '系统设置',
    description: '模型、语音和演示配置',
    to: '/settings',
    icon: Setting,
  },
]

const flowSteps = [
  {
    index: '01',
    title: '理解指令',
    description: '识别创建、查询、改期、删除等日历意图。',
    tone: 'active',
  },
  {
    index: '02',
    title: '校验上下文',
    description: '绑定当前空间、成员角色、参与人和时间范围。',
    tone: 'safe',
  },
  {
    index: '03',
    title: '执行工具',
    description: '通过受控 API 写入事件，并返回冲突提示。',
    tone: 'done',
  },
]

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const previewCells = [
  { day: 24 },
  { day: 25, label: '评审', active: true },
  { day: 26 },
  { day: 27 },
  { day: 28, label: '复盘', active: true },
  { day: 29 },
  { day: 30, label: '冲突', active: true },
  { day: 31 },
  { day: 1, label: '同步', active: true },
  { day: 2 },
  { day: 3 },
  { day: 4 },
  { day: 5 },
  { day: 6 },
]

const currentSpace = computed(() => workspace.currentSpace.value)

const currentSpaceDescription = computed(() => {
  if (workspace.state.isLoading) {
    return '正在同步可访问的个人和企业空间。'
  }
  if (!currentSpace.value) {
    return workspace.state.error || '暂无可访问空间，请先完成注册登录。'
  }
  return currentSpace.value.type === 'organization'
    ? `${roleLabel(currentSpace.value.role)} · 企业协作空间`
    : `${roleLabel(currentSpace.value.role)} · 个人日历空间`
})

const healthStatusClass = computed(() => {
  if (isHealthLoading.value) {
    return 'is-loading'
  }
  return health.value?.status === 'UP' ? 'is-online' : 'is-offline'
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
    return 'AI 工作台可以继续访问业务 API。'
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

function roleLabel(role: string) {
  const labels: Record<string, string> = {
    owner: 'Owner',
    admin: 'Admin',
    member: 'Member',
  }
  return labels[role] ?? role
}

onMounted(async () => {
  await Promise.allSettled([workspace.loadSpaces(), loadHealthStatus()])
})
</script>
