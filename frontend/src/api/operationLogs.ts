import { getAccessToken, request } from './http'
import type { QueryParams } from './types'

export type OperationLogRecord = {
  id: number
  userId: number
  userDisplayName: string
  calendarSpaceId: number
  calendarSpaceName: string
  operationSource: 'ai' | 'manual' | 'system' | string
  operationType: 'create' | 'update' | 'delete' | 'snooze' | 'undo' | string
  targetType: 'event' | 'reminder' | 'notification' | string
  targetId?: number | null
  beforeSnapshot?: string | null
  afterSnapshot?: string | null
  undoable: boolean
  undone: boolean
  undoExpiresAt?: string | null
  createdAt: string
}

export type OperationLogPage = {
  items: OperationLogRecord[]
  page: number
  size: number
  total: number
}

export type OperationLogParams = {
  calendarSpaceId?: number | null
  operationSource?: string
  targetType?: string
  page?: number
  size?: number
}

export function getOperationLogs(params: OperationLogParams, options: { showErrorMessage?: boolean } = {}) {
  return request<OperationLogPage>('/operation-logs', {
    params: cleanParams(params),
    showErrorMessage: options.showErrorMessage,
  })
}

export async function exportOperationLogs(params: Omit<OperationLogParams, 'page' | 'size'>) {
  const url = new URL('/api/operation-logs/export', window.location.origin)
  Object.entries(cleanParams(params)).forEach(([key, value]) => {
    url.searchParams.set(key, String(value))
  })
  const headers = new Headers()
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(url.toString(), { headers })
  if (!response.ok) {
    throw new Error(`导出失败 (${response.status})`)
  }
  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `operation-logs-${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(link.href)
}

function cleanParams(params: QueryParams) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  ) as QueryParams
}
