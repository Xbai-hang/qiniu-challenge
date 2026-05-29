import { ElMessage } from 'element-plus'
import type { ApiErrorPayload, ApiResponse, RequestOptions } from './types'

const DEFAULT_API_BASE_URL = '/api'
const DEFAULT_TIMEOUT_MS = 10000
const ACCESS_TOKEN_KEY = 'qiniu_challenge_access_token'

type ApiClientConfig = {
  baseUrl: string
  timeoutMs: number
}

type UnknownRecord = Record<string, unknown>

export class ApiClientError extends Error {
  code: string
  status?: number
  details?: unknown
  requestId?: string

  constructor(error: ApiErrorPayload, options: { status?: number; requestId?: string } = {}) {
    super(error.message)
    this.name = 'ApiClientError'
    this.code = error.code
    this.details = error.details
    this.status = options.status
    this.requestId = options.requestId
  }
}

const clientConfig: ApiClientConfig = {
  baseUrl: normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL),
  timeoutMs: Number(import.meta.env.VITE_API_TIMEOUT_MS) || DEFAULT_TIMEOUT_MS,
}

export function setAccessToken(token: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function clearAccessToken() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const controller = new AbortController()
  const timeoutMs = options.timeoutMs ?? clientConfig.timeoutMs
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetch(buildUrl(path, options.params), {
      method: options.method ?? 'GET',
      headers: buildHeaders(options.body, options.headers),
      body: serializeBody(options.body),
      signal: controller.signal,
    })
    const payload = await parseResponseBody<T>(response)

    if (isApiResponse<T>(payload)) {
      return unwrapApiResponse(payload, response.status)
    }

    throw new ApiClientError(
      {
        code: 'INVALID_RESPONSE',
        message: '服务返回格式异常',
        details: payload,
      },
      { status: response.status },
    )
  } catch (error) {
    const clientError = toApiClientError(error)

    if (options.showErrorMessage !== false) {
      ElMessage.error(clientError.message)
    }

    throw clientError
  } finally {
    window.clearTimeout(timeoutId)
  }
}

function normalizeBaseUrl(baseUrl: string) {
  return baseUrl.replace(/\/+$/, '')
}

function buildUrl(path: string, params?: RequestOptions['params']) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = new URL(`${clientConfig.baseUrl}${normalizedPath}`, window.location.origin)

  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined) {
      url.searchParams.set(key, String(value))
    }
  })

  return url.toString()
}

function buildHeaders(body: unknown, headers?: HeadersInit) {
  const requestHeaders = new Headers(headers)
  const token = getAccessToken()

  if (token) {
    requestHeaders.set('Authorization', `Bearer ${token}`)
  }

  if (body !== undefined && !(body instanceof FormData) && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json')
  }

  return requestHeaders
}

function serializeBody(body: unknown) {
  if (body === undefined) {
    return undefined
  }

  if (body instanceof FormData || body instanceof Blob || typeof body === 'string') {
    return body
  }

  return JSON.stringify(body)
}

async function parseResponseBody<T>(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return {
      success: true,
      data: undefined as T,
    }
  }

  const text = await response.text()

  if (!text) {
    return {
      success: false,
      error: {
        code: 'EMPTY_RESPONSE',
        message: '服务返回为空',
      },
    }
  }

  try {
    return JSON.parse(text)
  } catch {
    return {
      success: false,
      error: {
        code: 'INVALID_JSON',
        message: response.ok ? '服务返回格式异常' : `请求失败 (${response.status})`,
        details: text,
      },
    }
  }
}

function unwrapApiResponse<T>(payload: ApiResponse<T>, status: number) {
  if (payload.success) {
    return payload.data
  }

  throw new ApiClientError(payload.error, {
    status,
    requestId: payload.requestId,
  })
}

function isApiResponse<T>(payload: unknown): payload is ApiResponse<T> {
  if (!isRecord(payload) || typeof payload.success !== 'boolean') {
    return false
  }

  if (payload.success) {
    return 'data' in payload
  }

  return isRecord(payload.error) && typeof payload.error.code === 'string'
}

function toApiClientError(error: unknown) {
  if (error instanceof ApiClientError) {
    return error
  }

  if (error instanceof DOMException && error.name === 'AbortError') {
    return new ApiClientError({
      code: 'REQUEST_TIMEOUT',
      message: '请求超时，请稍后重试',
    })
  }

  return new ApiClientError({
    code: 'NETWORK_ERROR',
    message: '网络连接失败或服务未启动',
    details: error,
  })
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null
}

