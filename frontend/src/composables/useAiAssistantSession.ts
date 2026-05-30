import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  confirmPendingAction,
  getAiConversationMessages,
  getAiConversations,
  getPendingConfirmations,
  rejectPendingAction,
  sendAiChat,
  undoLastAiOperation,
  type AiConversation,
  type AiMessage,
  type AiToolCall,
  type PendingConfirmation,
} from '../api'
import { useWorkspaceStore } from '../stores/workspace'

export type AssistantChatMessage = {
  id: number
  role: 'user' | 'assistant'
  content: string
}

type AiAssistantState = {
  conversationId?: number
  messages: AssistantChatMessage[]
  conversations: AiConversation[]
  pendingConfirmations: PendingConfirmation[]
  lastToolCalls: AiToolCall[]
  canUndo: boolean
  isSending: boolean
  isConfirming: boolean
  isUndoing: boolean
  isLoadingHistory: boolean
  historyOpen: boolean
}

const state = reactive<AiAssistantState>({
  conversationId: undefined,
  messages: [],
  conversations: [],
  pendingConfirmations: [],
  lastToolCalls: [],
  canUndo: false,
  isSending: false,
  isConfirming: false,
  isUndoing: false,
  isLoadingHistory: false,
  historyOpen: false,
})

const draft = ref('')
let messageSeq = 1

export function useAiAssistantSession() {
  const workspace = useWorkspaceStore()
  const currentSpace = computed(() => workspace.currentSpace.value)

  async function sendMessage(content: string) {
    if (!currentSpace.value || !content.trim() || state.isSending) {
      return
    }

    const message = content.trim()
    state.messages.push({ id: messageSeq++, role: 'user', content: message })
    state.isSending = true
    try {
      const response = await sendAiChat(
        {
          calendarSpaceId: currentSpace.value.id,
          conversationId: state.conversationId,
          inputMode: 'text',
          message,
        },
        { showErrorMessage: false },
      )
      state.conversationId = response.conversationId
      state.messages.push({ id: messageSeq++, role: 'assistant', content: response.reply })
      state.pendingConfirmations = response.confirmations ?? []
      state.lastToolCalls = response.toolCalls ?? []
      state.canUndo = Boolean(response.resultCard?.actions?.includes('undo'))
      refreshCalendarIfNeeded()
      void loadConversations()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : 'AI 请求失败')
    } finally {
      state.isSending = false
    }
  }

  async function sendDraft() {
    const content = draft.value
    draft.value = ''
    await sendMessage(content)
  }

  async function sendQuickAction(action: string) {
    const prompts: Record<string, string> = {
      创建日程: '明天下午三点安排项目复盘',
      查询日程: '今天有什么安排？',
      检查冲突: '检查我今天的日程冲突',
      推荐时间: '帮我推荐一个明天下午开会的时间',
    }
    await sendMessage(prompts[action] ?? action)
  }

  async function loadConversations() {
    state.isLoadingHistory = true
    try {
      state.conversations = await getAiConversations({ showErrorMessage: false })
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '会话历史加载失败')
    } finally {
      state.isLoadingHistory = false
    }
  }

  async function selectConversation(conversationId: number) {
    state.conversationId = conversationId
    state.historyOpen = false
    state.lastToolCalls = []
    state.pendingConfirmations = []
    state.canUndo = false
    try {
      const messages = await getAiConversationMessages(conversationId, { showErrorMessage: false })
      state.messages = messages
        .filter(isVisibleMessage)
        .map(toChatMessage)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '会话消息加载失败')
    }
  }

  function startNewConversation() {
    state.conversationId = undefined
    state.messages = []
    state.pendingConfirmations = []
    state.lastToolCalls = []
    state.canUndo = false
    draft.value = ''
  }

  async function confirmConfirmation(confirmationId: number) {
    state.isConfirming = true
    try {
      const result = await confirmPendingAction(confirmationId, { showErrorMessage: false })
      state.pendingConfirmations = await getPendingConfirmations({ showErrorMessage: false })
      state.lastToolCalls = result.toolCall ? [result.toolCall] : state.lastToolCalls
      state.canUndo = Boolean(result.resultCard?.actions?.includes('undo'))
      state.messages.push({ id: messageSeq++, role: 'assistant', content: '已执行确认动作。' })
      refreshCalendarIfNeeded()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '确认动作失败')
    } finally {
      state.isConfirming = false
    }
  }

  async function rejectConfirmation(confirmationId: number) {
    state.isConfirming = true
    try {
      await rejectPendingAction(confirmationId, { showErrorMessage: false })
      state.pendingConfirmations = state.pendingConfirmations.filter((item) => item.id !== confirmationId)
      state.messages.push({ id: messageSeq++, role: 'assistant', content: '已取消这次高风险操作。' })
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '拒绝动作失败')
    } finally {
      state.isConfirming = false
    }
  }

  async function undoLast() {
    if (!currentSpace.value) {
      return
    }
    state.isUndoing = true
    try {
      const result = await undoLastAiOperation(currentSpace.value.id, { showErrorMessage: false })
      state.messages.push({ id: messageSeq++, role: 'assistant', content: result.summary })
      state.canUndo = false
      refreshCalendarIfNeeded()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '撤销失败')
    } finally {
      state.isUndoing = false
    }
  }

  return {
    currentSpace,
    draft,
    state,
    quickActions: ['创建日程', '查询日程', '检查冲突', '推荐时间'],
    sendDraft,
    sendMessage,
    sendQuickAction,
    loadConversations,
    selectConversation,
    startNewConversation,
    confirmConfirmation,
    rejectConfirmation,
    undoLast,
  }
}

function isVisibleMessage(message: AiMessage) {
  return message.role === 'user' || message.role === 'assistant'
}

function toChatMessage(message: AiMessage): AssistantChatMessage {
  return {
    id: message.id,
    role: message.role === 'user' ? 'user' : 'assistant',
    content: message.content,
  }
}

function refreshCalendarIfNeeded() {
  if (state.lastToolCalls.some((tool) => tool.status === 'succeeded')) {
    window.dispatchEvent(new CustomEvent('ai-calendar-updated'))
  }
}
