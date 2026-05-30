import { request } from './http'
import type { RequestOptions } from './types'

export type AiToolCall = {
  toolName: string
  riskLevel: 'low' | 'medium' | 'high' | 'critical' | string
  status: string
  data?: unknown
  confirmationRequired: boolean
}

export type AiResultCard = {
  type: string
  eventId?: number
  operationId?: number
  title?: string
  startTime?: string
  actions?: string[]
}

export type PendingConfirmation = {
  id: number
  conversationId: number
  calendarSpaceId: number
  actionType: string
  riskLevel: string
  summary: string
  payload: Record<string, unknown>
  status: string
  expiresAt: string
}

export type AiTaskState = {
  id: number
  conversationId: number
  calendarSpaceId: number
  taskType: string
  status: string
  draftPayload: Record<string, unknown>
  missingFields: string[]
  riskLevel?: string | null
  expiresAt?: string | null
}

export type AiChatResponse = {
  conversationId: number
  messageId: number
  reply: string
  resultCard?: AiResultCard | null
  toolCalls: AiToolCall[]
  confirmations: PendingConfirmation[]
  taskStates: AiTaskState[]
}

export type AiChatPayload = {
  calendarSpaceId: number
  conversationId?: number
  inputMode: 'text' | 'voice' | string
  message: string
}

export type ConfirmActionResponse = {
  status: string
  resultCard?: AiResultCard
  toolCall?: AiToolCall
}

export type UndoLastAiOperationResponse = {
  undone: boolean
  operationId: number
  summary: string
}

export type AiConversation = {
  id: number
  userId: number
  calendarSpaceId: number
  title?: string | null
  channel: string
  aiPromptVersion: string
  toolSchemaVersion: string
  modelProvider?: string | null
  modelName?: string | null
  status: string
  createdAt: string
  updatedAt: string
}

export type AiMessage = {
  id: number
  conversationId: number
  userId: number
  role: 'user' | 'assistant' | 'system' | 'tool' | string
  inputMode?: string | null
  content: string
  structuredPayload?: string | null
  transcriptionId?: number | null
  aiPromptVersion?: string | null
  toolSchemaVersion?: string | null
  modelProvider?: string | null
  modelName?: string | null
  createdAt: string
}

export function sendAiChat(payload: AiChatPayload, options: Pick<RequestOptions, 'showErrorMessage'> = {}) {
  return request<AiChatResponse>('/ai/chat', {
    method: 'POST',
    body: payload,
    timeoutMs: 45000,
    showErrorMessage: options.showErrorMessage,
  })
}

export function getAiConversations(options: Pick<RequestOptions, 'showErrorMessage'> = {}) {
  return request<AiConversation[]>('/ai/conversations', {
    showErrorMessage: options.showErrorMessage,
  })
}

export function getAiConversationMessages(
  conversationId: number,
  options: Pick<RequestOptions, 'showErrorMessage'> = {},
) {
  return request<AiMessage[]>(`/ai/conversations/${conversationId}/messages`, {
    showErrorMessage: options.showErrorMessage,
  })
}

export function getPendingConfirmations(options: Pick<RequestOptions, 'showErrorMessage'> = {}) {
  return request<PendingConfirmation[]>('/ai/confirmations', {
    showErrorMessage: options.showErrorMessage,
  })
}

export function confirmPendingAction(confirmationId: number, options: Pick<RequestOptions, 'showErrorMessage'> = {}) {
  return request<ConfirmActionResponse>(`/ai/confirmations/${confirmationId}/confirm`, {
    method: 'POST',
    showErrorMessage: options.showErrorMessage,
  })
}

export function rejectPendingAction(confirmationId: number, options: Pick<RequestOptions, 'showErrorMessage'> = {}) {
  return request<ConfirmActionResponse>(`/ai/confirmations/${confirmationId}/reject`, {
    method: 'POST',
    showErrorMessage: options.showErrorMessage,
  })
}

export function undoLastAiOperation(calendarSpaceId: number, options: Pick<RequestOptions, 'showErrorMessage'> = {}) {
  return request<UndoLastAiOperationResponse>('/ai/undo-last', {
    method: 'POST',
    body: { calendarSpaceId },
    showErrorMessage: options.showErrorMessage,
  })
}
