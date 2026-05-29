export type ApiErrorPayload = {
  code: string
  message: string
  details?: unknown
}

export type ApiSuccessResponse<T> = {
  success: true
  data: T
  requestId?: string
}

export type ApiFailureResponse = {
  success: false
  error: ApiErrorPayload
  requestId?: string
}

export type ApiResponse<T> = ApiSuccessResponse<T> | ApiFailureResponse

export type QueryParams = Record<string, string | number | boolean | null | undefined>

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type RequestOptions = {
  method?: HttpMethod
  params?: QueryParams
  body?: unknown
  headers?: HeadersInit
  timeoutMs?: number
  showErrorMessage?: boolean
}

