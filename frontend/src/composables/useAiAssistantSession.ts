import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  confirmPendingAction,
  deleteAiConversation,
  getAiConversationMessages,
  getAiConversations,
  getPendingConfirmations,
  rejectPendingAction,
  sendAiChat,
  fetchTtsAudio,
  synthesizeTts,
  transcribeAndChatAudio,
  undoLastAiOperation,
  type AiConversation,
  type AiMessage,
  type AiToolCall,
  type PendingConfirmation,
  type SpeechTranscription,
} from '../api'
import { useWorkspaceStore } from '../stores/workspace'

export type AssistantChatMessage = {
  id: number
  backendMessageId?: number
  role: 'user' | 'assistant'
  content: string
  inputMode?: 'text' | 'voice' | string | null
  audioUrl?: string
  transcriptText?: string
}

type AiAssistantState = {
  conversationId?: number
  messages: AssistantChatMessage[]
  conversations: AiConversation[]
  pendingConfirmations: PendingConfirmation[]
  lastToolCalls: AiToolCall[]
  lastTranscription?: SpeechTranscription
  lastAssistantMessageId?: number
  audioUrl?: string
  preparingSpeechMessageId?: number
  speakingMessageId?: number
  canUndo: boolean
  isSending: boolean
  isUploadingVoice: boolean
  isPreparingSpeech: boolean
  isSpeaking: boolean
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
  lastTranscription: undefined,
  lastAssistantMessageId: undefined,
  audioUrl: undefined,
  preparingSpeechMessageId: undefined,
  speakingMessageId: undefined,
  canUndo: false,
  isSending: false,
  isUploadingVoice: false,
  isPreparingSpeech: false,
  isSpeaking: false,
  isConfirming: false,
  isUndoing: false,
  isLoadingHistory: false,
  historyOpen: false,
})

const draft = ref('')
const speechAudio = ref<HTMLAudioElement | null>(null)
const userAudio = ref<HTMLAudioElement | null>(null)
const generatedVoiceUrls = new Set<string>()
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
      applyAiResponse(response)
      refreshCalendarIfNeeded()
      void loadConversations()
    } catch (error) {
      state.lastToolCalls = []
      state.pendingConfirmations = []
      state.canUndo = false
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

  async function sendVoice(audio: Blob) {
    if (!currentSpace.value || state.isSending || state.isUploadingVoice) {
      return
    }

    state.isUploadingVoice = true
    state.isSending = true
    state.lastTranscription = undefined
    const audioUrl = URL.createObjectURL(audio)
    generatedVoiceUrls.add(audioUrl)
    try {
      const response = await transcribeAndChatAudio(
        {
          file: audio,
          calendarSpaceId: currentSpace.value.id,
          conversationId: state.conversationId,
        },
        { showErrorMessage: false },
      )
      state.lastTranscription = response.transcription
      state.messages.push({
        id: messageSeq++,
        role: 'user',
        content: response.transcription.text,
        inputMode: 'voice',
        audioUrl,
        transcriptText: response.transcription.text,
      })
      applyAiResponse(response)
      refreshCalendarIfNeeded()
      void loadConversations()

      if (currentSpace.value.type === 'personal') {
        void playLatestReply()
      }
    } catch (error) {
      URL.revokeObjectURL(audioUrl)
      generatedVoiceUrls.delete(audioUrl)
      state.lastToolCalls = []
      state.pendingConfirmations = []
      state.canUndo = false
      ElMessage.error(error instanceof Error ? error.message : '语音识别失败，可改用键盘输入')
    } finally {
      state.isUploadingVoice = false
      state.isSending = false
    }
  }

  async function sendQuickAction(action: string) {
    const prompts: Record<string, string> = {
      创建日程: '请帮我创建日程：',
      查询日程: '请帮我查询日程：',
      检查冲突: '检查我今天的日程冲突',
      推荐时间: '帮我推荐一个明天下午开会的时间',
    }
    const prompt = prompts[action] ?? action
    if (action === '创建日程' || action === '查询日程') {
      draft.value = prompt
      return
    }
    await sendMessage(prompt)
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
    state.lastTranscription = undefined
    state.preparingSpeechMessageId = undefined
    state.speakingMessageId = undefined
    stopUserVoice()
    stopSpeech()
    try {
      const messages = await getAiConversationMessages(conversationId, { showErrorMessage: false })
      state.messages = messages
        .filter(isVisibleMessage)
        .map(toChatMessage)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '会话消息加载失败')
    }
  }

  async function deleteConversation(conversationId: number) {
    try {
      await deleteAiConversation(conversationId, { showErrorMessage: false })
      state.conversations = state.conversations.filter((conversation) => conversation.id !== conversationId)
      if (state.conversationId === conversationId) {
        startNewConversation()
      }
      ElMessage.success('会话记录已删除')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '删除会话失败')
    }
  }

  function startNewConversation() {
    state.conversationId = undefined
    state.messages = []
    state.pendingConfirmations = []
    state.lastToolCalls = []
    state.lastTranscription = undefined
    state.lastAssistantMessageId = undefined
    state.preparingSpeechMessageId = undefined
    state.speakingMessageId = undefined
    state.canUndo = false
    draft.value = ''
    revokeVoiceMessageUrls()
    stopUserVoice()
    stopSpeech()
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

  async function playLatestReply() {
    const assistantMessage = [...state.messages].reverse().find((message) => message.role === 'assistant')
    if (!assistantMessage) {
      return
    }
    await playAssistantMessage(assistantMessage)
  }

  async function playAssistantMessage(message: AssistantChatMessage) {
    if (message.role !== 'assistant' || state.isPreparingSpeech) {
      return
    }

    state.isPreparingSpeech = true
    state.preparingSpeechMessageId = message.id
    try {
      stopSpeech()
      const tts = await synthesizeTts(
        {
          messageId: message.backendMessageId ?? state.lastAssistantMessageId,
          text: message.content,
        },
        { showErrorMessage: false },
      )
      const audioBlob = await fetchTtsAudio(tts.audioUrl)
      const audioUrl = URL.createObjectURL(audioBlob)
      const audio = new Audio(audioUrl)
      speechAudio.value = audio
      state.audioUrl = audioUrl
      state.isSpeaking = true
      state.speakingMessageId = message.id
      audio.onended = () => {
        state.isSpeaking = false
        state.speakingMessageId = undefined
      }
      audio.onpause = () => {
        state.isSpeaking = false
        state.speakingMessageId = undefined
      }
      await audio.play()
    } catch (error) {
      stopSpeech()
      ElMessage.warning(error instanceof Error ? error.message : '语音播报暂不可用')
    } finally {
      state.isPreparingSpeech = false
      state.preparingSpeechMessageId = undefined
    }
  }

  async function playUserVoice(message: AssistantChatMessage) {
    if (!message.audioUrl) {
      return
    }
    stopUserVoice()
    const audio = new Audio(message.audioUrl)
    userAudio.value = audio
    await audio.play()
  }

  function pauseSpeech() {
    speechAudio.value?.pause()
    state.isSpeaking = false
    state.speakingMessageId = undefined
  }

  function stopSpeech() {
    if (speechAudio.value) {
      speechAudio.value.pause()
      speechAudio.value = null
    }
    if (state.audioUrl) {
      URL.revokeObjectURL(state.audioUrl)
      state.audioUrl = undefined
    }
    state.isSpeaking = false
    state.speakingMessageId = undefined
  }

  function stopUserVoice() {
    if (userAudio.value) {
      userAudio.value.pause()
      userAudio.value = null
    }
  }

  function revokeVoiceMessageUrls() {
    generatedVoiceUrls.forEach((url) => URL.revokeObjectURL(url))
    generatedVoiceUrls.clear()
  }

  return {
    currentSpace,
    draft,
    state,
    quickActions: ['创建日程', '查询日程', '检查冲突', '推荐时间'],
    sendDraft,
    sendMessage,
    sendVoice,
    sendQuickAction,
    loadConversations,
    selectConversation,
    deleteConversation,
    startNewConversation,
    confirmConfirmation,
    rejectConfirmation,
    undoLast,
    playLatestReply,
    playAssistantMessage,
    playUserVoice,
    pauseSpeech,
    stopSpeech,
    stopUserVoice,
  }
}

function applyAiResponse(response: {
  conversationId: number
  messageId: number
  reply: string
  confirmations?: PendingConfirmation[]
  toolCalls?: AiToolCall[]
  resultCard?: { actions?: string[] } | null
}) {
  state.conversationId = response.conversationId
  state.lastAssistantMessageId = response.messageId
  state.messages.push({
    id: messageSeq++,
    backendMessageId: response.messageId,
    role: 'assistant',
    content: response.reply,
    inputMode: 'text',
  })
  state.pendingConfirmations = response.confirmations ?? []
  state.lastToolCalls = response.toolCalls ?? []
  state.canUndo = Boolean(response.resultCard?.actions?.includes('undo'))
}

function isVisibleMessage(message: AiMessage) {
  return message.role === 'user' || message.role === 'assistant'
}

function toChatMessage(message: AiMessage): AssistantChatMessage {
  return {
    id: message.id,
    backendMessageId: message.id,
    role: message.role === 'user' ? 'user' : 'assistant',
    content: message.content,
    inputMode: message.inputMode,
    transcriptText: message.inputMode === 'voice' ? message.content : undefined,
  }
}

function refreshCalendarIfNeeded() {
  if (state.lastToolCalls.some((tool) => tool.status === 'succeeded')) {
    window.dispatchEvent(new CustomEvent('ai-calendar-updated'))
  }
}
