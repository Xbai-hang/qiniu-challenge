<template>
  <div
    :class="['global-ai-assistant', { expanded: isExpanded, dragging: dragState.dragging }]"
    :style="assistantStyle"
  >
    <section v-if="isExpanded" class="global-ai-panel" aria-label="AI 助手">
      <header class="global-ai-header" @pointerdown="startDrag">
        <span class="global-ai-mark" aria-hidden="true">
          <ChatDotRound />
        </span>
        <div>
          <strong>AI 助手</strong>
          <small>{{ currentSpace ? currentSpace.name : '等待日历空间' }}</small>
        </div>
        <button type="button" class="global-ai-icon-button" aria-label="新建会话" @click="ai.startNewConversation">
          <Plus />
        </button>
        <button type="button" class="global-ai-icon-button" aria-label="历史会话" @click="toggleHistory">
          <Clock />
        </button>
        <button type="button" class="global-ai-icon-button" aria-label="最大化 AI 对话" @click="maximizeChat">
          <FullScreen />
        </button>
        <button type="button" class="global-ai-icon-button" aria-label="缩小 AI 助手" @click="isExpanded = false">
          <Minus />
        </button>
      </header>

      <section v-if="ai.state.historyOpen" class="global-ai-history">
        <div class="mini-section-heading">
          <strong>会话历史</strong>
          <span>{{ ai.state.conversations.length }}</span>
        </div>
        <div v-if="ai.state.isLoadingHistory" class="global-ai-empty">正在加载历史会话</div>
        <div
          v-for="conversation in ai.state.conversations"
          v-else
          :key="conversation.id"
          :class="['global-ai-history-item', ai.state.conversationId === conversation.id ? 'active' : '']"
        >
          <button type="button" class="ai-history-select" @click="ai.selectConversation(conversation.id)">
            <strong>{{ conversation.title || 'AI 对话' }}</strong>
            <span>{{ formatConversationTime(conversation.updatedAt) }}</span>
          </button>
          <button type="button" class="ai-history-delete" aria-label="删除会话" @click="ai.deleteConversation(conversation.id)">
            <Delete />
          </button>
        </div>
      </section>

      <div class="global-ai-messages">
        <div v-if="ai.state.messages.length === 0" class="global-ai-greeting">
          <strong>你好，我是你的 AI 日历助手</strong>
          <span>需要我帮你安排或查询日程吗？</span>
        </div>
        <AiVoiceMessage
          v-for="message in ai.state.messages"
          :key="message.id"
          :message="message"
          :preparing-message-id="ai.state.preparingSpeechMessageId"
          :speaking-message-id="ai.state.speakingMessageId"
          @play-user="ai.playUserVoice"
          @play-assistant="ai.playAssistantMessage"
          @pause-assistant="ai.pauseSpeech"
        />
      </div>

      <div class="global-ai-quick-actions">
        <button v-for="item in ai.quickActions" :key="item" type="button" @click="ai.sendQuickAction(item)">
          {{ item }}
        </button>
      </div>

      <div class="global-ai-voice-strip">
        <button
          type="button"
          :class="['global-ai-voice-button', { recording: voice.isRecording.value }]"
          :disabled="!currentSpace || ai.state.isUploadingVoice || (ai.state.isSending && !voice.isRecording.value)"
          :aria-label="voice.isRecording.value ? '停止语音输入' : '开始语音输入'"
          @click="voice.toggleRecording"
        >
          <Microphone />
          <span>{{ voiceStatusText }}</span>
        </button>
        <small>长按空格</small>
      </div>

      <div v-if="voice.recordingError.value" class="global-ai-transcription">
        {{ voice.recordingError.value }}
      </div>

      <section v-if="ai.state.pendingConfirmations.length > 0" class="global-ai-confirmation">
        <strong>{{ ai.state.pendingConfirmations[0].summary }}</strong>
        <div>
          <button type="button" :disabled="ai.state.isConfirming" @click="ai.rejectConfirmation(ai.state.pendingConfirmations[0].id)">
            拒绝
          </button>
          <button type="button" class="danger" :disabled="ai.state.isConfirming" @click="ai.confirmConfirmation(ai.state.pendingConfirmations[0].id)">
            确认
          </button>
        </div>
      </section>

      <div v-if="ai.state.lastToolCalls.length > 0 || ai.state.canUndo" class="global-ai-status">
        <span v-for="tool in ai.state.lastToolCalls" :key="`${tool.toolName}-${tool.status}`">
          {{ tool.toolName }} · {{ tool.status }}
        </span>
        <button v-if="ai.state.canUndo" type="button" :disabled="ai.state.isUndoing" @click="ai.undoLast">
          <RefreshLeft />
          撤销
        </button>
      </div>

      <form class="global-ai-input" @submit.prevent="ai.sendDraft">
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

    <button
      v-else
      type="button"
      class="global-ai-orb"
      aria-label="展开 AI 助手"
      @click="openFromOrb"
      @pointerdown="startDrag"
    >
      <ChatDotRound />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ChatDotRound, Clock, Delete, FullScreen, Microphone, Minus, Plus, Promotion, RefreshLeft } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import AiVoiceMessage from './AiVoiceMessage.vue'
import { useAiAssistantSession } from '../composables/useAiAssistantSession'
import { shouldIgnoreVoiceShortcut, useVoiceRecorder } from '../composables/useVoiceRecorder'
import { useWorkspaceStore } from '../stores/workspace'

const workspace = useWorkspaceStore()
const ai = useAiAssistantSession()
const router = useRouter()
const route = useRoute()
const currentSpace = computed(() => workspace.currentSpace.value)
const isExpanded = ref(false)
const POSITION_STORAGE_KEY = 'qiniu_global_ai_position'
const ORB_SIZE = 72
const PANEL_WIDTH = 360
const PANEL_MIN_HEIGHT = 420
const position = reactive({ x: 0, y: 0 })
const dragState = reactive({
  dragging: false,
  moved: false,
  pointerId: 0,
  startPointerX: 0,
  startPointerY: 0,
  startX: 0,
  startY: 0,
})

const assistantStyle = computed(() => ({
  left: `${position.x}px`,
  top: `${position.y}px`,
}))

const voice = useVoiceRecorder(async (audio) => {
  await ai.sendVoice(audio)
})

const voiceStatusText = computed(() => {
  if (voice.isRecording.value) {
    return '录音中，松开发送'
  }
  if (ai.state.isUploadingVoice) {
    return '上传中'
  }
  if (ai.state.isSending) {
    return '理解中'
  }
  return '语音输入'
})

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

async function maximizeChat() {
  isExpanded.value = false
  await router.push({ name: 'ai-chat' })
}

function openFromOrb() {
  if (dragState.moved) {
    dragState.moved = false
    return
  }
  isExpanded.value = true
  clampPosition()
  savePosition()
}

function startDrag(event: PointerEvent) {
  if (event.button !== 0) {
    return
  }
  const target = event.target as HTMLElement | null
  if (isExpanded.value && target?.closest('button, input, a')) {
    return
  }
  dragState.dragging = true
  dragState.moved = false
  dragState.pointerId = event.pointerId
  dragState.startPointerX = event.clientX
  dragState.startPointerY = event.clientY
  dragState.startX = position.x
  dragState.startY = position.y
  window.addEventListener('pointermove', handleDragMove)
  window.addEventListener('pointerup', stopDrag)
  window.addEventListener('pointercancel', stopDrag)
}

function handleDragMove(event: PointerEvent) {
  if (!dragState.dragging || event.pointerId !== dragState.pointerId) {
    return
  }
  const nextX = dragState.startX + event.clientX - dragState.startPointerX
  const nextY = dragState.startY + event.clientY - dragState.startPointerY
  if (Math.abs(event.clientX - dragState.startPointerX) > 4 || Math.abs(event.clientY - dragState.startPointerY) > 4) {
    dragState.moved = true
  }
  position.x = nextX
  position.y = nextY
  clampPosition()
}

function stopDrag(event: PointerEvent) {
  if (event.pointerId !== dragState.pointerId) {
    return
  }
  dragState.dragging = false
  savePosition()
  window.removeEventListener('pointermove', handleDragMove)
  window.removeEventListener('pointerup', stopDrag)
  window.removeEventListener('pointercancel', stopDrag)
  window.setTimeout(() => {
    dragState.moved = false
  }, 0)
}

function clampPosition() {
  const width = isExpanded.value ? Math.min(PANEL_WIDTH, window.innerWidth - 32) : ORB_SIZE
  const height = isExpanded.value ? Math.min(PANEL_MIN_HEIGHT, window.innerHeight - 80) : ORB_SIZE
  const padding = 16
  position.x = Math.min(Math.max(padding, position.x), Math.max(padding, window.innerWidth - width - padding))
  position.y = Math.min(Math.max(padding, position.y), Math.max(padding, window.innerHeight - height - padding))
}

function savePosition() {
  localStorage.setItem(POSITION_STORAGE_KEY, JSON.stringify({ x: position.x, y: position.y }))
}

function restorePosition() {
  const stored = localStorage.getItem(POSITION_STORAGE_KEY)
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as { x?: number; y?: number }
      if (typeof parsed.x === 'number' && typeof parsed.y === 'number') {
        position.x = parsed.x
        position.y = parsed.y
        clampPosition()
        return
      }
    } catch {
      localStorage.removeItem(POSITION_STORAGE_KEY)
    }
  }
  position.x = window.innerWidth - ORB_SIZE - 28
  position.y = window.innerHeight - ORB_SIZE - 28
  clampPosition()
}

function handleResize() {
  clampPosition()
  savePosition()
}

function handleVoiceKeyDown(event: KeyboardEvent) {
  if (route.name === 'ai-chat' || shouldIgnoreVoiceShortcut(event) || event.repeat || !currentSpace.value) {
    return
  }
  event.preventDefault()
  if (!isExpanded.value) {
    isExpanded.value = true
    clampPosition()
    savePosition()
  }
  void voice.startRecording()
}

function handleVoiceKeyUp(event: KeyboardEvent) {
  if (route.name === 'ai-chat' || shouldIgnoreVoiceShortcut(event)) {
    return
  }
  event.preventDefault()
  voice.stopRecording()
}

onMounted(() => {
  restorePosition()
  void ai.loadConversations()
  window.addEventListener('resize', handleResize)
  window.addEventListener('keydown', handleVoiceKeyDown)
  window.addEventListener('keyup', handleVoiceKeyUp)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleVoiceKeyDown)
  window.removeEventListener('keyup', handleVoiceKeyUp)
  window.removeEventListener('pointermove', handleDragMove)
  window.removeEventListener('pointerup', stopDrag)
  window.removeEventListener('pointercancel', stopDrag)
  voice.cleanupRecorder()
})
</script>
