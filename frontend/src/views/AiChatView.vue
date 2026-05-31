<template>
  <main class="ai-chat-page">
    <section class="ai-chat-history-panel">
      <header class="ai-chat-section-header">
        <div>
          <p class="eyebrow">Conversations</p>
          <h1>AI 会话</h1>
        </div>
        <button type="button" class="ai-chat-icon-button" aria-label="新建会话" @click="ai.startNewConversation">
          <Plus />
        </button>
      </header>

      <button type="button" class="ai-chat-new" @click="ai.startNewConversation">
        <Plus />
        <span>开始新对话</span>
      </button>

      <div class="ai-chat-history-list">
        <div v-if="ai.state.isLoadingHistory" class="ai-chat-empty">正在加载历史会话</div>
        <div
          v-for="conversation in ai.state.conversations"
          v-else
          :key="conversation.id"
          :class="['ai-chat-history-item', ai.state.conversationId === conversation.id ? 'active' : '']"
        >
          <button type="button" class="ai-history-select" @click="ai.selectConversation(conversation.id)">
            <strong>{{ conversation.title || 'AI 对话' }}</strong>
            <span>{{ formatConversationTime(conversation.updatedAt) }}</span>
          </button>
          <button type="button" class="ai-history-delete" aria-label="删除会话" @click="ai.deleteConversation(conversation.id)">
            <Delete />
          </button>
        </div>
      </div>
    </section>

    <section class="ai-chat-main-panel">
      <header class="ai-chat-main-header">
        <div>
          <p class="eyebrow">AI Agent Console</p>
          <h2>语音日历助手</h2>
          <span>{{ currentSpace ? currentSpace.name : '等待日历空间' }}</span>
        </div>
        <div class="ai-chat-status">
          <span v-if="ai.state.conversationId">#{{ ai.state.conversationId }}</span>
          <span>{{ ai.state.lastToolCalls[0]?.toolName || 'ready' }}</span>
        </div>
      </header>

      <div class="ai-chat-message-list">
        <div v-if="ai.state.messages.length === 0" class="ai-chat-welcome">
          <strong>你好，我是你的 AI 日历助手</strong>
          <span>可以直接安排日程、查询日程、处理确认和撤销最近操作。</span>
        </div>
        <article v-for="message in ai.state.messages" :key="message.id" :class="['ai-chat-message', message.role]">
          <span>{{ message.role === 'user' ? '你' : 'AI' }}</span>
          <p>{{ message.content }}</p>
        </article>
      </div>

      <section v-if="ai.state.pendingConfirmations.length > 0" class="ai-chat-confirmation">
        <div>
          <p class="eyebrow">Pending Action</p>
          <strong>{{ ai.state.pendingConfirmations[0].summary }}</strong>
        </div>
        <div class="ai-chat-confirm-actions">
          <button type="button" :disabled="ai.state.isConfirming" @click="ai.rejectConfirmation(ai.state.pendingConfirmations[0].id)">
            拒绝
          </button>
          <button type="button" class="danger" :disabled="ai.state.isConfirming" @click="ai.confirmConfirmation(ai.state.pendingConfirmations[0].id)">
            确认执行
          </button>
        </div>
      </section>

      <div v-if="ai.state.lastToolCalls.length > 0 || ai.state.canUndo" class="ai-chat-tool-strip">
        <span v-for="tool in ai.state.lastToolCalls" :key="`${tool.toolName}-${tool.status}`">
          {{ tool.toolName }} · {{ tool.status }}
        </span>
        <button v-if="ai.state.canUndo" type="button" :disabled="ai.state.isUndoing" @click="ai.undoLast">
          <RefreshLeft />
          撤销
        </button>
      </div>

      <div class="ai-chat-quick-actions">
        <button v-for="item in ai.quickActions" :key="item" type="button" @click="ai.sendQuickAction(item)">
          {{ item }}
        </button>
      </div>

      <form class="ai-chat-input" @submit.prevent="ai.sendDraft">
        <input
          v-model.trim="ai.draft.value"
          type="text"
          :disabled="ai.state.isSending || !currentSpace"
          placeholder="输入你的指令..."
          aria-label="AI 指令"
        />
        <button type="submit" :disabled="ai.state.isSending || !currentSpace || !ai.draft.value" aria-label="发送">
          <Promotion />
        </button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Delete, Plus, Promotion, RefreshLeft } from '@element-plus/icons-vue'
import { useAiAssistantSession } from '../composables/useAiAssistantSession'
import { useWorkspaceStore } from '../stores/workspace'

const ai = useAiAssistantSession()
const workspace = useWorkspaceStore()
const currentSpace = computed(() => workspace.currentSpace.value)

function formatConversationTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

onMounted(() => {
  void ai.loadConversations()
})
</script>
