<template>
  <main class="app-shell">
    <section class="panel assistant-panel">
      <p class="eyebrow">AI Agent Console</p>
      <h1>语音日历 AI Native</h1>
      <p class="summary">
        项目骨架已就绪。后续 PR 将逐步接入健康检查、认证、日历事件、提醒、AI Agent、语音识别和 TTS。
      </p>
      <div class="voice-orb" aria-hidden="true"></div>
    </section>

    <section class="panel workspace-panel">
      <div class="workspace-header">
        <span>Workspace</span>
        <span class="status">Bootstrap</span>
      </div>
      <div class="calendar-grid" aria-label="Calendar skeleton">
        <div v-for="day in days" :key="day" class="calendar-cell">
          <span>{{ day }}</span>
        </div>
      </div>
    </section>

    <section class="panel insight-panel">
      <p class="eyebrow">Next PR</p>
      <h2>/api/health</h2>
      <p>下一步将新增后端健康检查接口，并让前端可以探测后端服务状态。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
const days = Array.from({ length: 14 }, (_, index) => index + 1)
</script>
