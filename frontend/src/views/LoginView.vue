<template>
  <main class="auth-page">
    <section class="auth-visual" aria-hidden="true">
      <div class="auth-pulse">
        <span></span>
        <span></span>
        <span></span>
      </div>
      <div class="auth-timeline">
        <div v-for="item in timelineItems" :key="item.title" class="auth-timeline-item">
          <strong>{{ item.time }}</strong>
          <span>{{ item.title }}</span>
        </div>
      </div>
    </section>

    <section class="auth-panel" aria-labelledby="auth-title">
      <RouterLink class="auth-brand" to="/">
        <span class="brand-mark" aria-hidden="true">AI</span>
        <span>
          <strong>语音日历</strong>
          <small>AI Native Productivity OS</small>
        </span>
      </RouterLink>

      <div class="auth-copy">
        <p class="eyebrow">Secure Workspace</p>
        <h1 id="auth-title">{{ mode === 'login' ? '登录工作台' : '创建账号' }}</h1>
        <p>
          {{ mode === 'login' ? '恢复你的 AI 日程上下文，继续管理今天和接下来的安排。' : '注册后即可进入个人 AI 工作台，后续会自动接入个人空间。' }}
        </p>
      </div>

      <div class="auth-tabs" role="tablist" aria-label="认证方式">
        <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label v-if="mode === 'register'">
          <span>用户名</span>
          <input v-model.trim="registerForm.username" autocomplete="username" placeholder="alice" />
        </label>

        <label>
          <span>{{ mode === 'login' ? '账号' : '邮箱' }}</span>
          <input
            v-if="mode === 'login'"
            v-model.trim="loginForm.account"
            autocomplete="username"
            placeholder="用户名或邮箱"
          />
          <input
            v-else
            v-model.trim="registerForm.email"
            autocomplete="email"
            placeholder="alice@example.com"
          />
        </label>

        <label v-if="mode === 'register'">
          <span>展示名</span>
          <input v-model.trim="registerForm.displayName" autocomplete="name" placeholder="Alice" />
        </label>

        <label>
          <span>密码</span>
          <input
            v-if="mode === 'login'"
            v-model="loginForm.password"
            autocomplete="current-password"
            placeholder="Password123"
            type="password"
          />
          <input
            v-else
            v-model="registerForm.password"
            autocomplete="new-password"
            placeholder="至少 8 位，含大小写字母和数字"
            type="password"
          />
        </label>

        <p v-if="formError" class="auth-error">{{ formError }}</p>

        <button type="submit" class="auth-submit" :disabled="auth.state.isLoading">
          <Loading v-if="auth.state.isLoading" />
          <span>{{ mode === 'login' ? '登录' : '注册并进入' }}</span>
        </button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

type AuthMode = 'login' | 'register'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref<AuthMode>('login')
const formError = ref('')

const loginForm = reactive({
  account: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  email: '',
  displayName: '',
  password: '',
})

const timelineItems = [
  { time: '09:30', title: '同步今日安排' },
  { time: '11:00', title: '识别会议冲突' },
  { time: '16:20', title: '准备项目复盘' },
]

async function handleSubmit() {
  formError.value = ''

  try {
    if (mode.value === 'login') {
      validateLogin()
      await auth.signIn(loginForm)
    } else {
      validateRegister()
      await auth.signUp(registerForm)
    }

    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '操作失败，请稍后重试'
  }
}

function validateLogin() {
  if (!loginForm.account || !loginForm.password) {
    throw new Error('请输入账号和密码')
  }
}

function validateRegister() {
  if (!registerForm.username || !registerForm.email || !registerForm.displayName || !registerForm.password) {
    throw new Error('请完整填写注册信息')
  }

  if (!/^[A-Za-z0-9_]{3,64}$/.test(registerForm.username)) {
    throw new Error('用户名只能包含字母、数字和下划线，长度至少 3 位')
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) {
    throw new Error('请输入有效邮箱')
  }

  if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/.test(registerForm.password)) {
    throw new Error('密码至少 8 位，并包含大小写字母和数字')
  }
}
</script>
