import { buildApiUrl, getAccessToken, request } from './http'
import type { AiChatResponse } from './ai'
import type { RequestOptions } from './types'

export type SpeechTranscription = {
  transcriptionId: number
  text: string
  provider: string
  confidence?: number | null
  durationMs?: number | null
}

export type SpeechChatResponse = AiChatResponse & {
  transcription: SpeechTranscription
}

export type TtsSynthesizePayload = {
  messageId?: number
  text: string
  voice?: string
}

export type TtsSynthesizeResponse = {
  ttsId: number
  audioUrl: string
  expiresAt: string
}

export function transcribeAudio(
  payload: {
    file: Blob
    calendarSpaceId: number
    conversationId?: number
  },
  options: Pick<RequestOptions, 'showErrorMessage'> = {},
) {
  const formData = audioFormData(payload.file, payload.calendarSpaceId, payload.conversationId)
  return request<SpeechTranscription>('/speech/transcribe', {
    method: 'POST',
    body: formData,
    timeoutMs: 45000,
    showErrorMessage: options.showErrorMessage,
  })
}

export function transcribeAndChatAudio(
  payload: {
    file: Blob
    calendarSpaceId: number
    conversationId?: number
  },
  options: Pick<RequestOptions, 'showErrorMessage'> = {},
) {
  const formData = audioFormData(payload.file, payload.calendarSpaceId, payload.conversationId)
  return request<SpeechChatResponse>('/speech/transcribe-and-chat', {
    method: 'POST',
    body: formData,
    timeoutMs: 60000,
    showErrorMessage: options.showErrorMessage,
  })
}

export function synthesizeTts(
  payload: TtsSynthesizePayload,
  options: Pick<RequestOptions, 'showErrorMessage'> = {},
) {
  return request<TtsSynthesizeResponse>('/tts/synthesize', {
    method: 'POST',
    body: payload,
    timeoutMs: 30000,
    showErrorMessage: options.showErrorMessage,
  })
}

export async function fetchTtsAudio(audioUrl: string) {
  const token = getAccessToken()
  const headers = new Headers()

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(buildApiUrl(audioUrl.replace(/^\/api/, '')), {
    headers,
  })

  if (!response.ok) {
    throw new Error(`TTS 音频获取失败 (${response.status})`)
  }

  return response.blob()
}

function audioFormData(file: Blob, calendarSpaceId: number, conversationId?: number) {
  const formData = new FormData()
  const filename = `voice-${Date.now()}.${audioExtension(file.type)}`
  formData.append('file', file, filename)
  formData.append('calendarSpaceId', String(calendarSpaceId))

  if (conversationId !== undefined) {
    formData.append('conversationId', String(conversationId))
  }

  return formData
}

function audioExtension(contentType: string) {
  if (contentType.includes('wav')) {
    return 'wav'
  }
  if (contentType.includes('mpeg') || contentType.includes('mp3')) {
    return 'mp3'
  }
  if (contentType.includes('ogg')) {
    return 'ogg'
  }
  if (contentType.includes('mp4') || contentType.includes('m4a')) {
    return 'm4a'
  }
  return 'webm'
}
