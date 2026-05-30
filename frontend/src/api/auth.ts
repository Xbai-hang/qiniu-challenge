import { clearAccessToken, request, setAccessToken } from './http'

export type AuthUser = {
  id: number
  username: string
  email: string
  displayName: string
}

export type AuthTokenResponse = {
  accessToken: string
  user: AuthUser
}

export type RegisterResponse = AuthTokenResponse & {
  defaultSpaceId?: number
}

export type LoginPayload = {
  account: string
  password: string
}

export type RegisterPayload = {
  username: string
  email: string
  displayName: string
  password: string
}

export async function login(payload: LoginPayload) {
  const data = await request<AuthTokenResponse>('/auth/login', {
    method: 'POST',
    body: payload,
  })
  setAccessToken(data.accessToken)
  return data
}

export async function register(payload: RegisterPayload) {
  const data = await request<RegisterResponse>('/auth/register', {
    method: 'POST',
    body: payload,
  })
  setAccessToken(data.accessToken)
  return data
}

export async function logout() {
  try {
    await request<boolean>('/auth/logout', {
      method: 'POST',
      showErrorMessage: false,
    })
  } finally {
    clearAccessToken()
  }
}

export function getCurrentUser(options: { showErrorMessage?: boolean } = {}) {
  return request<AuthUser>('/users/me', {
    showErrorMessage: options.showErrorMessage,
  })
}
