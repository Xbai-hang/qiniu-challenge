<template>
  <main class="route-page">
    <section class="panel page-hero">
      <p class="eyebrow">Calendar</p>
      <h1>日历页占位</h1>
      <p class="summary">后续将在这里接入月历、表格、甘特视图和个人/企业空间事件数据。</p>
    </section>

    <section class="panel placeholder-panel">
      <div class="month-strip">
        <span v-for="day in weekDays" :key="day">{{ day }}</span>
      </div>
      <div class="calendar-grid calendar-grid-large" aria-label="Calendar placeholder">
        <div v-for="day in 35" :key="day" class="calendar-cell">
          <span>{{ day }}</span>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
</script>
