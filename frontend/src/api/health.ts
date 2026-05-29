import { request } from './http'
import type { RequestOptions } from './types'

export type HealthStatus = {
  status: string
  version: string
  time: string
}

export function getHealthStatus(options: Pick<RequestOptions, 'showErrorMessage'> = {}) {
  return request<HealthStatus>('/health', options)
}
