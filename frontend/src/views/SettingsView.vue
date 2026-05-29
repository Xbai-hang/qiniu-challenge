<template>
  <main class="route-page settings-page">
    <section class="panel page-hero">
      <p class="eyebrow">Settings</p>
      <h1>设置页占位</h1>
      <p class="summary">后续将在这里接入账号、空间、AI 模型、语音识别和 TTS 配置。</p>
    </section>

    <section class="settings-grid">
      <article v-for="item in settingGroups" :key="item.title" class="panel setting-card">
        <span class="setting-icon" aria-hidden="true">{{ item.icon }}</span>
        <div>
          <h2>{{ item.title }}</h2>
          <p>{{ item.description }}</p>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
const settingGroups = [
  {
    icon: '◎',
    title: '空间与权限',
    description: '管理个人空间、企业空间、成员角色和邀请码。',
  },
  {
    icon: '◐',
    title: 'AI 与语音',
    description: '配置模型供应商、语音识别、TTS 和 Agent 执行策略。',
  },
  {
    icon: '◇',
    title: '提醒与通知',
    description: '设置站内提醒、默认提醒时间和消息偏好。',
  },
]
</script>
