import { request } from './http'
import type { QueryParams, RequestOptions } from './types'

export type EventParticipant = {
  userId: number
  displayName: string
  role: 'organizer' | 'attendee' | string
  responseStatus: 'needs_action' | 'accepted' | 'declined' | string
}

export type EventEnterpriseFields = {
  project?: string
  ownerUserId?: number | null
  status?: string
  priority?: string
  tags?: string[]
  eventType?: string
  customFields?: string
}

export type CalendarEvent = {
  id: number
  calendarSpaceId: number
  organizationId?: number | null
  createdBy: number
  title: string
  description?: string | null
  location?: string | null
  startTime: string
  endTime: string
  timezone: string
  allDay: boolean
  visibility: string
  source: string
  repeatType: string
  repeatUntil?: string | null
  repeatCount?: number | null
  repeatRuleText?: string | null
  project?: string | null
  ownerUserId?: number | null
  status?: string | null
  priority?: string | null
  tags: string[]
  eventType?: string | null
  notes?: string | null
  customFields?: string | null
  version: number
  participants: EventParticipant[]
  conflicts: EventConflict[]
  requiresConfirmation: boolean
}

export type EventConflict = {
  eventId: number
  calendarSpaceId?: number
  calendarSpaceName?: string
  title: string
  participantUserId: number
  participantName: string
  startTime: string
  endTime: string
}

export type ConflictCheckPayload = {
  calendarSpaceId: number
  eventId?: number
  participantUserIds?: number[]
  startTime: string
  endTime: string
}

export type ConflictCheckResult = {
  hasConflict: boolean
  conflicts: EventConflict[]
  requiresConfirmation: boolean
}

export type EventPayload = {
  calendarSpaceId?: number
  title?: string
  startTime?: string
  endTime?: string
  location?: string
  description?: string
  timezone?: string
  allDay?: boolean
  visibility?: string
  source?: string
  repeatType?: string
  repeatUntil?: string
  repeatCount?: number
  repeatRuleText?: string
  notes?: string
  version?: number
  participantUserIds?: number[]
  forceCreateOnConflict?: boolean
  forceUpdateOnConflict?: boolean
  enterpriseFields?: EventEnterpriseFields
}

export type EventListParams = {
  calendarSpaceId?: number
  spaceId?: number
  start?: string
  end?: string
  keyword?: string
  project?: string
  ownerUserId?: number
  status?: string
  priority?: string
  tag?: string
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
}

export function createCalendarEvent(payload: EventPayload, options: Pick<RequestOptions, 'showErrorMessage'> = {}) {
  return request<CalendarEvent>('/events', {
    method: 'POST',
    body: payload,
    showErrorMessage: options.showErrorMessage,
  })
}

export function getCalendarEvents(params: EventListParams, options: { showErrorMessage?: boolean } = {}) {
  return request<CalendarEvent[]>('/events', {
    params: cleanParams(params),
    showErrorMessage: options.showErrorMessage,
  })
}

export function getCalendarEvent(eventId: number, options: { showErrorMessage?: boolean } = {}) {
  return request<CalendarEvent>(`/events/${eventId}`, {
    showErrorMessage: options.showErrorMessage,
  })
}

export function updateCalendarEvent(
  eventId: number,
  payload: EventPayload,
  options: Pick<RequestOptions, 'showErrorMessage'> = {},
) {
  return request<CalendarEvent>(`/events/${eventId}`, {
    method: 'PATCH',
    body: payload,
    showErrorMessage: options.showErrorMessage,
  })
}

export function deleteCalendarEvent(eventId: number) {
  return request<boolean>(`/events/${eventId}`, {
    method: 'DELETE',
  })
}

export function checkEventConflicts(payload: ConflictCheckPayload) {
  return request<ConflictCheckResult>('/events/conflicts/check', {
    method: 'POST',
    body: payload,
  })
}

export function searchCalendarEvents(
  params: Pick<EventListParams, 'calendarSpaceId' | 'spaceId' | 'keyword'> & { limit?: number },
  options: { showErrorMessage?: boolean } = {},
) {
  return request<CalendarEvent[]>('/events/search', {
    params: cleanParams(params),
    showErrorMessage: options.showErrorMessage,
  })
}

function cleanParams(params: QueryParams) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  ) as QueryParams
}
