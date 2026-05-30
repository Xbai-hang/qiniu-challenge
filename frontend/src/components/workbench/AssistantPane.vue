<template>
  <aside class="ai-workbench-pane assistant-pane" aria-labelledby="assistant-title">
    <div class="assistant-intro">
      <p class="eyebrow">AI Agent Console</p>
      <h2 id="assistant-title">语音日历助手</h2>
      <p>{{ currentSpace ? `正在管理「${currentSpace.name}」` : '请选择一个日历空间' }}</p>
    </div>

    <button type="button" class="voice-command" aria-label="开始语音输入">
      <span class="voice-orb compact" aria-hidden="true">
        <Microphone />
      </span>
    </button>

    <div class="ai-input-row">
      <input type="text" value="今天下午有什么安排？" aria-label="AI 文本输入" readonly />
      <button type="button" @click="$emit('create')">
        <Plus />
      </button>
    </div>

    <div class="agent-timeline" aria-label="AI 执行时间线">
      <div v-for="item in timelineItems" :key="item.title" class="agent-step">
        <span :class="['agent-dot', item.tone]" aria-hidden="true"></span>
        <div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.description }}</p>
        </div>
      </div>
    </div>

    <section class="pending-action-card">
      <div>
        <p class="eyebrow">Tool Calls</p>
        <strong>{{ isLoading ? '正在同步事件' : `${events.length} 个事件已载入` }}</strong>
      </div>
      <span class="tool-call">{{ currentSpace?.type === 'organization' ? 'enterprise.view' : 'personal.view' }}</span>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Microphone, Plus } from '@element-plus/icons-vue'
import type { CalendarEvent, CalendarSpace } from '../../api'

defineEmits<{
  create: []
}>()

const props = defineProps<{
  currentSpace: CalendarSpace | null
  events: CalendarEvent[]
  isLoading: boolean
  conflictCount: number
}>()

const timelineItems = computed(() => [
  {
    title: '空间上下文',
    description: props.currentSpace ? `${props.currentSpace.name} · ${spaceTypeLabel(props.currentSpace.type)}` : '等待空间列表',
    tone: 'is-running',
  },
  {
    title: '冲突扫描',
    description: props.conflictCount > 0 ? `发现 ${props.conflictCount} 个冲突事件` : '当前视图未发现时间重叠',
    tone: props.conflictCount > 0 ? 'is-warning' : 'is-success',
  },
  {
    title: '手动工具',
    description: '月历、表格和甘特拖拽会复用事件更新接口',
    tone: 'is-idle',
  },
])

function spaceTypeLabel(type: CalendarSpace['type']) {
  return type === 'organization' ? '企业空间' : '个人空间'
}
</script>
