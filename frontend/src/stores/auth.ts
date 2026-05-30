import { reactive, readonly } from 'vue'
import {
  clearAccessToken,
  getAccessToken,
  getCurrentUser,
  login,
  logout,
  register,
  type AuthUser,
  type LoginPayload,
  type RegisterPayload,
} from '../api'

type AuthState = {
  user: AuthUser | null
  isReady: boolean
  isLoading: boolean
}

const state = reactive<AuthState>({
  user: null,
  isReady: false,
  isLoading: false,
})

let restorePromise: Promise<AuthUser | null> | null = null

export function useAuthStore() {
  async function restoreSession() {
    if (state.isReady) {
      return state.user
    }

    if (restorePromise) {
      return restorePromise
    }

    restorePromise = doRestoreSession()
    return restorePromise
  }

  async function signIn(payload: LoginPayload) {
    state.isLoading = true
    try {
      const data = await login(payload)
      state.user = data.user
      state.isReady = true
      return data.user
    } finally {
      state.isLoading = false
    }
  }

  async function signUp(payload: RegisterPayload) {
    state.isLoading = true
    try {
      const data = await register(payload)
      state.user = data.user
      state.isReady = true
      return data.user
    } finally {
      state.isLoading = false
    }
  }

  async function signOut() {
    state.isLoading = true
    try {
      await logout()
    } finally {
      state.user = null
      state.isReady = true
      state.isLoading = false
    }
  }

  return {
    state: readonly(state),
    restoreSession,
    signIn,
    signUp,
    signOut,
  }
}

async function doRestoreSession() {
  const token = getAccessToken()
  if (!token) {
    state.user = null
    state.isReady = true
    restorePromise = null
    return null
  }

  state.isLoading = true
  try {
    state.user = await getCurrentUser({ showErrorMessage: false })
    return state.user
  } catch {
    clearAccessToken()
    state.user = null
    return null
  } finally {
    state.isReady = true
    state.isLoading = false
    restorePromise = null
  }
}
