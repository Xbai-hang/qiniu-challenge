<template>
  <button type="button" :class="['agenda-item', conflict ? 'has-conflict' : '']" @click="$emit('edit')">
    <span class="agenda-time">{{ formatTimeRange(event.startTime, event.endTime) }}</span>
    <strong>{{ event.title }}</strong>
    <small>{{ event.project || event.location || event.description || '无补充信息' }}</small>
    <span v-if="conflict" class="conflict-mini">冲突</span>
  </button>
</template>

<script setup lang="ts">
import type { CalendarEvent } from '../../api'

defineEmits<{
  edit: []
}>()

defineProps<{
  event: CalendarEvent
  conflict: boolean
}>()

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
})

function formatTime(value: string) {
  return timeFormatter.format(new Date(value))
}

function formatTimeRange(start: string, end: string) {
  return `${formatTime(start)} - ${formatTime(end)}`
}
</script>
