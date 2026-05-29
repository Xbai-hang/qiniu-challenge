import { request } from './http'

export type HealthStatus = {
  status: string
  version: string
  time: string
}

export function getHealthStatus() {
  return request<HealthStatus>('/health')
}

