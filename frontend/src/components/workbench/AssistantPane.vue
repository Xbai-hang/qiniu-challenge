<template>
  <aside class="ai-workbench-pane assistant-pane" aria-labelledby="assistant-title">
    <div class="assistant-intro">
      <div class="assistant-title-row">
        <div>
          <p class="eyebrow">AI Agent Console</p>
          <h2 id="assistant-title">语音日历助手</h2>
        </div>
        <div class="assistant-session-actions">
          <button type="button" title="新建会话" @click="ai.startNewConversation">
            <Plus />
          </button>
          <button type="button" title="历史会话" @click="toggleHistory">
            <Clock />
          </button>
        </div>
      </div>
      <p>{{ currentSpace ? `正在管理「${currentSpace.name}」` : '请选择一个日历空间' }}</p>
    </div>

    <section v-if="ai.state.historyOpen" class="assistant-history-panel">
      <div class="mini-section-heading">
        <strong>会话历史</strong>
        <span>{{ ai.state.conversations.length }}</span>
      </div>
      <div v-if="ai.state.isLoadingHistory" class="assistant-empty">正在加载历史会话</div>
      <button
        v-for="conversation in ai.state.conversations"
        v-else
        :key="conversation.id"
        type="button"
        :class="['assistant-history-item', ai.state.conversationId === conversation.id ? 'active' : '']"
        @click="ai.selectConversation(conversation.id)"
      >
        <strong>{{ conversation.title || 'AI 对话' }}</strong>
        <span>{{ formatConversationTime(conversation.updatedAt) }}</span>
      </button>
    </section>

    <button type="button" class="voice-command" aria-label="开始语音输入">
      <span class="voice-orb compact" aria-hidden="true">
        <Microphone />
      </span>
    </button>

    <form class="ai-input-row" @submit.prevent="ai.sendDraft">
      <input
        v-model.trim="ai.draft.value"
        type="text"
        aria-label="AI 文本输入"
        :disabled="ai.state.isSending || !currentSpace"
        placeholder="今天有什么安排？"
      />
      <button type="submit" :disabled="ai.state.isSending || !currentSpace || !ai.draft.value">
        <Promotion />
      </button>
    </form>

    <div class="assistant-chat-log" aria-label="AI 对话记录">
      <div v-for="message in ai.state.messages" :key="message.id" :class="['assistant-message', message.role]">
        <span>{{ message.role === 'user' ? '你' : 'AI' }}</span>
        <p>{{ message.content }}</p>
      </div>
      <div v-if="ai.state.messages.length === 0" class="assistant-empty">
        试试“今天有什么安排”或“明天下午三点安排项目复盘”。
      </div>
    </div>

    <div class="agent-timeline" aria-label="AI 执行时间线">
      <div v-for="item in timelineItems" :key="item.title" class="agent-step">
        <span :class="['agent-dot', item.tone]" aria-hidden="true"></span>
        <div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.description }}</p>
        </div>
      </div>
    </div>

    <section v-if="ai.state.pendingConfirmations.length > 0" class="pending-action-card confirmation-card">
      <div>
        <p class="eyebrow">Pending Action</p>
        <strong>{{ ai.state.pendingConfirmations[0].summary }}</strong>
      </div>
      <span class="tool-call danger">{{ ai.state.pendingConfirmations[0].actionType }}</span>
      <div class="assistant-actions">
        <button
          type="button"
          class="secondary-action"
          :disabled="ai.state.isConfirming"
          @click="ai.rejectConfirmation(ai.state.pendingConfirmations[0].id)"
        >
          拒绝
        </button>
        <button
          type="button"
          class="primary-action danger"
          :disabled="ai.state.isConfirming"
          @click="ai.confirmConfirmation(ai.state.pendingConfirmations[0].id)"
        >
          确认执行
        </button>
      </div>
    </section>

    <section class="pending-action-card">
      <div>
        <p class="eyebrow">Tool Calls</p>
        <strong>{{ isLoading ? '正在同步事件' : `${events.length} 个事件已载入` }}</strong>
      </div>
      <span class="tool-call">{{ currentSpace?.type === 'organization' ? 'enterprise.view' : 'personal.view' }}</span>
      <div v-if="ai.state.lastToolCalls.length > 0" class="tool-call-list">
        <span
          v-for="tool in ai.state.lastToolCalls"
          :key="`${tool.toolName}-${tool.status}`"
          :class="['tool-call', tool.status]"
        >
          {{ tool.toolName }} · {{ tool.status }}
        </span>
      </div>
      <button
        v-if="ai.state.canUndo"
        type="button"
        class="assistant-undo"
        :disabled="ai.state.isUndoing || !currentSpace"
        @click="ai.undoLast"
      >
        <RefreshLeft />
        <span>撤销最近 AI 操作</span>
      </button>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Clock, Microphone, Plus, Promotion, RefreshLeft } from '@element-plus/icons-vue'
import type { CalendarEvent, CalendarSpace } from '../../api'
import { useAiAssistantSession } from '../../composables/useAiAssistantSession'

defineEmits<{
  create: []
  refresh: []
}>()

const props = defineProps<{
  currentSpace: CalendarSpace | null
  events: CalendarEvent[]
  isLoading: boolean
  conflictCount: number
}>()

const ai = useAiAssistantSession()

const timelineItems = computed(() => [
  {
    title: '空间上下文',
    description: props.currentSpace ? `${props.currentSpace.name} · ${spaceTypeLabel(props.currentSpace.type)}` : '等待空间列表',
    tone: 'is-running',
  },
  {
    title: '冲突扫描',
    description: props.conflictCount > 0 ? `发现 ${props.conflictCount} 个冲突事件` : '当前视图未发现时间重叠',
    tone: props.conflictCount > 0 ? 'is-warning' : 'is-success',
  },
  {
    title: ai.state.lastToolCalls.length > 0 ? '最近工具' : 'AI 工具',
    description: ai.state.lastToolCalls[0]
      ? `${ai.state.lastToolCalls[0].toolName} · ${ai.state.lastToolCalls[0].status}`
      : '等待自然语言指令',
    tone: ai.state.isSending ? 'is-running' : 'is-idle',
  },
])

async function toggleHistory() {
  ai.state.historyOpen = !ai.state.historyOpen
  if (ai.state.historyOpen) {
    await ai.loadConversations()
  }
}

function formatConversationTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function spaceTypeLabel(type: CalendarSpace['type']) {
  return type === 'organization' ? '企业空间' : '个人空间'
}

onMounted(() => {
  void ai.loadConversations()
})
</script>
