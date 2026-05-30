import { request } from './http'

export type CalendarSpace = {
  id: number
  type: 'personal' | 'organization'
  name: string
  organizationId?: number
  role: 'owner' | 'admin' | 'member'
}

export function getCalendarSpaces(options: { showErrorMessage?: boolean } = {}) {
  return request<CalendarSpace[]>('/spaces', {
    showErrorMessage: options.showErrorMessage,
  })
}
