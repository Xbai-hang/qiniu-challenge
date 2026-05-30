<template>
  <aside class="ai-workbench-pane insight-feed" aria-labelledby="insight-title">
    <div class="insight-header">
      <div>
        <p class="eyebrow">Intelligence Feed</p>
        <h2 id="insight-title">日程情报</h2>
      </div>
      <span class="status">{{ upcomingEvents.length }} 个即将到来</span>
    </div>

    <section class="focus-card">
      <p class="eyebrow">Today Focus</p>
      <strong>{{ todayEvents.length ? todayEvents[0]?.title : '今天暂无重点事项' }}</strong>
      <span>{{ todayEvents.length ? `今天还有 ${todayEvents.length} 个事件` : '可以从中间工作区创建新日程' }}</span>
    </section>

    <section class="insight-section">
      <div class="mini-section-heading">
        <strong>未来 7 天</strong>
        <span>{{ upcomingEvents.length }}</span>
      </div>
      <div v-if="upcomingEvents.length === 0" class="mini-empty">近期没有事件。</div>
      <button
        v-for="event in upcomingEvents"
        v-else
        :key="event.id"
        type="button"
        :class="['upcoming-item', conflictIds.has(event.id) ? 'has-conflict' : '']"
        @click="$emit('edit', event)"
      >
        <span>{{ formatDate(event.startTime) }}</span>
        <strong>{{ event.title }}</strong>
        <small>{{ formatTimeRange(event.startTime, event.endTime) }}</small>
      </button>
    </section>

    <section class="insight-section">
      <div class="mini-section-heading">
        <strong>冲突提示</strong>
        <span>{{ conflictIds.size }}</span>
      </div>
      <div v-if="conflictEvents.length === 0" class="mini-empty">当前范围没有冲突事件。</div>
      <button
        v-for="event in conflictEvents"
        v-else
        :key="event.id"
        type="button"
        class="conflict-insight"
        @click="$emit('edit', event)"
      >
        <WarningFilled />
        <span>{{ event.title }}</span>
      </button>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import type { CalendarEvent } from '../../api'

defineEmits<{
  edit: [event: CalendarEvent]
}>()

const props = defineProps<{
  events: CalendarEvent[]
  conflictIds: Set<number>
}>()

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  weekday: 'short',
})

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
})

const todayEvents = computed(() => {
  const today = startOfDay(new Date()).getTime()
  return props.events.filter((event) => startOfDay(new Date(event.startTime)).getTime() === today)
})

const upcomingEvents = computed(() => {
  const now = Date.now()
  const end = now + 7 * 24 * 60 * 60 * 1000
  return props.events
    .filter((event) => {
      const start = new Date(event.startTime).getTime()
      return start >= now && start <= end
    })
    .slice(0, 7)
})

const conflictEvents = computed(() => props.events.filter((event) => props.conflictIds.has(event.id)).slice(0, 6))

function startOfDay(date: Date) {
  const copy = new Date(date)
  copy.setHours(0, 0, 0, 0)
  return copy
}

function formatDate(value: string) {
  return dateFormatter.format(new Date(value))
}

function formatTime(value: string) {
  return timeFormatter.format(new Date(value))
}

function formatTimeRange(start: string, end: string) {
  return `${formatTime(start)} - ${formatTime(end)}`
}
</script>
