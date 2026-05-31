<template>
  <section class="calendar-workspace-panel" aria-labelledby="workspace-title">
    <header class="workspace-command-bar">
      <div class="space-switcher">
        <span class="space-dot" aria-hidden="true"></span>
        <select :value="selectedSpaceId ?? ''" aria-label="切换日历空间" @change="handleSpaceChange">
          <option value="" disabled>选择空间</option>
          <option v-for="space in spaces" :key="space.id" :value="space.id">
            {{ space.name }} · {{ space.type === 'organization' ? '企业' : '个人' }}
          </option>
        </select>
      </div>

      <div class="workspace-month-nav">
        <button type="button" class="icon-button" aria-label="上个月" @click="$emit('prev-month')">
          <ArrowLeft />
        </button>
        <div>
          <p class="eyebrow">Workspace</p>
          <h1 id="workspace-title">{{ monthLabel }}</h1>
        </div>
        <button type="button" class="icon-button" aria-label="下个月" @click="$emit('next-month')">
          <ArrowRight />
        </button>
      </div>

      <div class="workspace-actions">
        <button type="button" class="secondary-action compact-action" @click="$emit('today')">今天</button>
        <button type="button" class="primary-action compact-action" @click="$emit('create')">
          <Plus />
          <span>新建</span>
        </button>
      </div>
    </header>

    <div class="workspace-tabs" role="tablist" aria-label="工作区视图">
      <button
        v-for="mode in viewModes"
        :key="mode.value"
        type="button"
        role="tab"
        :aria-selected="viewMode === mode.value"
        :class="{ active: viewMode === mode.value }"
        @click="$emit('change-view', mode.value)"
      >
        <component :is="mode.icon" />
        <span>{{ mode.label }}</span>
      </button>
    </div>

    <div class="workspace-filter-strip" aria-label="事件筛选">
      <input
        :value="filters.keyword"
        type="search"
        placeholder="搜索标题、备注、项目"
        aria-label="关键词"
        @input="updateFilter('keyword', ($event.target as HTMLInputElement).value)"
      />
      <input
        :value="filters.project"
        type="text"
        placeholder="项目"
        aria-label="项目"
        @input="updateFilter('project', ($event.target as HTMLInputElement).value)"
      />
      <select
        :value="filters.status"
        aria-label="状态"
        @change="updateFilter('status', ($event.target as HTMLSelectElement).value)"
      >
        <option value="">全部状态</option>
        <option value="todo">待处理</option>
        <option value="in_progress">进行中</option>
        <option value="done">已完成</option>
        <option value="blocked">阻塞</option>
      </select>
      <select
        :value="filters.priority"
        aria-label="优先级"
        @change="updateFilter('priority', ($event.target as HTMLSelectElement).value)"
      >
        <option value="">全部优先级</option>
        <option value="high">高优先级</option>
        <option value="medium">中优先级</option>
        <option value="low">低优先级</option>
      </select>
      <input
        :value="filters.tag"
        type="text"
        placeholder="标签"
        aria-label="标签"
        @input="updateFilter('tag', ($event.target as HTMLInputElement).value)"
      />
      <button type="button" class="secondary-action compact-action" :disabled="isLoading" @click="$emit('load')">
        <Refresh />
        <span>刷新</span>
      </button>
    </div>

    <div v-if="isLoading" class="workspace-loading">正在加载日历事件…</div>

    <div
      v-show="viewMode === 'month'"
      :class="['month-focus-layout', dayFocusCollapsed ? 'is-focus-collapsed' : '']"
      aria-label="月历视图"
    >
      <div class="month-view">
        <div class="weekday-row">
          <span v-for="weekday in weekdays" :key="weekday">{{ weekday }}</span>
        </div>
        <div class="month-grid">
          <div
            v-for="day in monthDays"
            :key="day.key"
            role="button"
            tabindex="0"
            :style="day.columnStart ? { gridColumnStart: String(day.columnStart) } : undefined"
            :class="[
              'month-cell',
              sameDay(day.date, selectedDate) ? 'is-selected' : '',
            ]"
            @click="selectCalendarDate(day.date)"
            @keydown.enter="selectCalendarDate(day.date)"
            @keydown.space.prevent="selectCalendarDate(day.date)"
            @dragover.prevent
            @drop="dropOnDate(day.date)"
          >
            <span class="day-number">{{ day.date.getDate() }}</span>
            <span class="day-load">{{ eventsByDay.get(day.key)?.length ?? 0 }}</span>
            <span class="month-event-stack">
              <button
                v-for="event in (eventsByDay.get(day.key) ?? []).slice(0, 3)"
                :key="event.id"
                type="button"
                draggable="true"
                :class="['month-event-block', conflictIds.has(event.id) ? 'has-conflict' : '']"
                @click.stop="$emit('edit', event)"
                @dragstart.stop="dragEventId = event.id"
                @dragend="dragEventId = null"
              >
                {{ event.title }}
              </button>
            </span>
          </div>
        </div>
      </div>

      <aside :class="['day-focus-panel', dayFocusCollapsed ? 'is-collapsed' : '']" aria-label="选中日期安排">
        <button
          type="button"
          class="day-focus-toggle"
          :aria-expanded="!dayFocusCollapsed"
          :aria-label="dayFocusCollapsed ? '显示日程面板' : '隐藏日程面板'"
          :title="dayFocusCollapsed ? '显示日程面板' : '隐藏日程面板'"
          @click="dayFocusCollapsed = !dayFocusCollapsed"
        >
          <ArrowRight v-if="!dayFocusCollapsed" />
          <ArrowLeft v-else />
        </button>

        <button
          v-if="dayFocusCollapsed"
          type="button"
          class="day-focus-collapsed-card"
          aria-label="显示选中日期安排"
          @click="dayFocusCollapsed = false"
          @dragover.prevent
          @drop="dropOnDate(selectedDate)"
        >
          <span>{{ selectedDateWeekday }}</span>
          <strong>{{ selectedDateDayNumber }}</strong>
          <small>{{ selectedDateEvents.length }}</small>
        </button>

        <section v-if="!dayFocusCollapsed" class="mini-month-card" aria-labelledby="mini-month-title">
          <div class="mini-month-header">
            <strong id="mini-month-title">{{ miniMonthLabel }}</strong>
            <span>{{ selectedDateEvents.length }} 项</span>
          </div>
          <div class="mini-weekday-row">
            <span v-for="weekday in miniWeekdays" :key="weekday">{{ weekday }}</span>
          </div>
          <div class="mini-month-grid">
            <button
              v-for="day in miniMonthDays"
              :key="day.key"
              type="button"
              :class="[
                'mini-day',
                day.isCurrentMonth ? '' : 'is-muted',
                sameDay(day.date, selectedDate) ? 'is-selected' : '',
                eventsByDay.get(day.key)?.length ? 'has-events' : '',
              ]"
              @click="selectCalendarDate(day.date)"
              @dragover.prevent
              @drop="dropOnDate(day.date)"
            >
              {{ day.date.getDate() }}
            </button>
          </div>
        </section>

        <section v-if="!dayFocusCollapsed" class="day-timeline-card" aria-labelledby="day-timeline-title">
          <div class="day-timeline-header">
            <div>
              <span>{{ selectedDateWeekday }}</span>
              <strong id="day-timeline-title">{{ selectedDateDayNumber }}</strong>
            </div>
            <p>{{ selectedDateLabel }}</p>
          </div>
          <div class="day-timeline-scroll">
            <div class="day-now-marker" v-if="showNowMarker" :style="{ top: `${nowMarkerOffset}px` }">
              <span></span>
            </div>
            <div v-for="hour in dayHours" :key="hour" class="timeline-hour">
              <span class="timeline-hour-label">{{ formatHour(hour) }}</span>
              <div class="timeline-hour-track">
                <button
                  v-for="event in eventsForHour(hour)"
                  :key="event.id"
                  type="button"
                  draggable="true"
                  :class="['timeline-event', conflictIds.has(event.id) ? 'has-conflict' : '']"
                  @click="$emit('edit', event)"
                  @dragstart.stop="dragEventId = event.id"
                  @dragend="dragEventId = null"
                >
                  <strong>{{ event.title }}</strong>
                  <span>{{ formatTimeRange(event.startTime, event.endTime) }}</span>
                </button>
              </div>
            </div>
            <div v-if="selectedDateEvents.length === 0" class="timeline-empty">这一天没有事件。</div>
          </div>
        </section>
      </aside>
    </div>

    <div v-show="viewMode === 'list'" class="agenda-view" aria-label="日程列表视图">
      <div class="agenda-column">
        <div class="mini-section-heading">
          <strong>{{ selectedDateLabel }}</strong>
          <span>{{ selectedDateEvents.length }}</span>
        </div>
        <EventListItem
          v-for="event in selectedDateEvents"
          :key="event.id"
          :event="event"
          :conflict="conflictIds.has(event.id)"
          @edit="$emit('edit', event)"
        />
        <div v-if="selectedDateEvents.length === 0" class="mini-empty">选中日期没有事件。</div>
      </div>
      <div class="agenda-column">
        <div class="mini-section-heading">
          <strong>未来 7 天</strong>
          <span>{{ futureEvents.length }}</span>
        </div>
        <EventListItem
          v-for="event in futureEvents"
          :key="event.id"
          :event="event"
          :conflict="conflictIds.has(event.id)"
          @edit="$emit('edit', event)"
        />
        <div v-if="futureEvents.length === 0" class="mini-empty">近期没有事件。</div>
      </div>
    </div>

    <div v-show="viewMode === 'table'" class="enterprise-table-wrap" aria-label="企业表格视图">
      <table class="enterprise-table">
        <thead>
          <tr>
            <th>日程</th>
            <th>时间</th>
            <th>项目</th>
            <th>负责人</th>
            <th>状态</th>
            <th>优先级</th>
            <th>标签</th>
            <th>冲突</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="event in sortedEvents"
            :key="event.id"
            :class="{ 'has-conflict': conflictIds.has(event.id) }"
            @click="$emit('edit', event)"
          >
            <td>
              <strong>{{ event.title }}</strong>
              <span>{{ event.location || event.description || '无补充信息' }}</span>
            </td>
            <td>{{ formatDate(event.startTime) }} {{ formatTimeRange(event.startTime, event.endTime) }}</td>
            <td>{{ event.project || '-' }}</td>
            <td>{{ event.ownerUserId ? memberNames.get(event.ownerUserId) || `用户 ${event.ownerUserId}` : '-' }}</td>
            <td>{{ event.status ? statusLabel(event.status) : '-' }}</td>
            <td>
              <span v-if="event.priority" :class="['priority-pill', `is-${event.priority}`]">
                {{ priorityLabel(event.priority) }}
              </span>
              <span v-else>-</span>
            </td>
            <td>
              <span v-for="tag in event.tags" :key="tag" class="table-tag">#{{ tag }}</span>
            </td>
            <td>{{ conflictIds.has(event.id) ? '冲突' : '正常' }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="sortedEvents.length === 0" class="mini-empty">当前筛选下没有事件。</div>
    </div>

    <div v-show="viewMode === 'gantt'" class="gantt-view" aria-label="甘特排期视图">
      <div class="gantt-axis">
        <span v-for="day in ganttDays" :key="day.key">{{ day.label }}</span>
      </div>
      <div class="gantt-lanes">
        <div v-for="group in ganttGroups" :key="group.name" class="gantt-row">
          <div class="gantt-row-label">{{ group.name }}</div>
          <div class="gantt-track" @dragover.prevent>
            <button
              v-for="event in group.events"
              :key="event.id"
              type="button"
              draggable="true"
              :class="['gantt-block', conflictIds.has(event.id) ? 'has-conflict' : '']"
              :style="ganttStyle(event)"
              @click="$emit('edit', event)"
              @dragstart="dragEventId = event.id"
              @dragend="dragEventId = null"
              @drop.stop
            >
              {{ event.title }}
            </button>
            <button
              v-for="day in ganttDays"
              :key="`${group.name}-${day.key}`"
              type="button"
              class="gantt-drop-zone"
              :style="{ left: `${day.offset * 100}%`, width: `${100 / ganttDays.length}%` }"
              :aria-label="`移动到 ${day.label}`"
              @drop="dropOnDate(day.date)"
            ></button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  Calendar,
  Finished,
  Grid,
  Plus,
  Refresh,
  Tickets,
} from '@element-plus/icons-vue'
import type { CalendarEvent, CalendarSpace } from '../../api'
import EventListItem from './EventListItem.vue'

export type WorkspaceViewMode = 'month' | 'list' | 'table' | 'gantt'

export type WorkspaceFilters = {
  keyword: string
  project: string
  status: string
  priority: string
  tag: string
  sortBy: string
  sortDirection: 'asc' | 'desc'
}

const props = defineProps<{
  spaces: readonly CalendarSpace[]
  selectedSpaceId: number | null
  currentSpace: CalendarSpace | null
  events: readonly CalendarEvent[]
  selectedDate: Date
  monthCursor: Date
  viewMode: WorkspaceViewMode
  filters: WorkspaceFilters
  conflictIds: Set<number>
  memberNames: Map<number, string>
  isLoading: boolean
}>()

const emit = defineEmits<{
  'select-space': [spaceId: number]
  'prev-month': []
  'next-month': []
  today: []
  'select-date': [date: Date]
  create: []
  edit: [event: CalendarEvent]
  'drop-calendar': [eventId: number, date: Date]
  'change-view': [mode: WorkspaceViewMode]
  'set-month': [date: Date]
  'update-filter': [key: keyof WorkspaceFilters, value: string]
  load: []
}>()

const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const miniWeekdays = ['S', 'M', 'T', 'W', 'T', 'F', 'S']
const dayHours = Array.from({ length: 24 }, (_, index) => index)
const timelineHourHeight = 48
const dragEventId = ref<number | null>(null)
const dayFocusCollapsed = ref(false)

const viewModes = [
  { value: 'month' as const, label: '月历', icon: Calendar },
  { value: 'list' as const, label: '列表', icon: Tickets },
  { value: 'table' as const, label: '表格', icon: Grid },
  { value: 'gantt' as const, label: '甘特', icon: Finished },
]

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
})

const fullDateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long',
})

const monthFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
})

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
})

const monthLabel = computed(() => monthFormatter.format(props.monthCursor))
const miniMonthLabel = computed(() => {
  const month = new Intl.DateTimeFormat('en-US', { month: 'short' }).format(props.monthCursor)
  return `${month} ${props.monthCursor.getFullYear()}`
})

const monthDays = computed(() => {
  const year = props.monthCursor.getFullYear()
  const month = props.monthCursor.getMonth()
  const totalDays = new Date(year, month + 1, 0).getDate()
  const firstDate = new Date(year, month, 1)
  const firstColumn = ((firstDate.getDay() + 6) % 7) + 1

  return Array.from({ length: totalDays }, (_, index) => {
    const date = new Date(year, month, index + 1)
    return {
      date,
      key: dateKey(date),
      columnStart: index === 0 ? firstColumn : undefined,
    }
  })
})

const miniMonthDays = computed(() => {
  const year = props.monthCursor.getFullYear()
  const month = props.monthCursor.getMonth()
  const firstDate = new Date(year, month, 1)
  const gridStart = new Date(firstDate)
  gridStart.setDate(firstDate.getDate() - firstDate.getDay())

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart)
    date.setDate(gridStart.getDate() + index)
    return {
      date,
      key: dateKey(date),
      isCurrentMonth: date.getMonth() === month,
    }
  })
})

const eventsByDay = computed(() => {
  const map = new Map<string, CalendarEvent[]>()
  props.events.forEach((event) => {
    const key = dateKey(new Date(event.startTime))
    const list = map.get(key) ?? []
    list.push(event)
    map.set(key, list)
  })
  return map
})

const selectedDateEvents = computed(() =>
  props.events
    .filter((event) => sameDay(new Date(event.startTime), props.selectedDate))
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()),
)

const selectedDateLabel = computed(() => fullDateFormatter.format(props.selectedDate))
const selectedDateWeekday = computed(() =>
  new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(props.selectedDate).toUpperCase(),
)
const selectedDateDayNumber = computed(() => props.selectedDate.getDate())
const showNowMarker = computed(() => sameDay(new Date(), props.selectedDate))
const nowMarkerOffset = computed(() => {
  const now = new Date()
  return ((now.getHours() * 60 + now.getMinutes()) / 60) * timelineHourHeight
})

const futureEvents = computed(() => {
  const start = startOfDay(props.selectedDate).getTime()
  const end = start + 7 * 24 * 60 * 60 * 1000
  return props.events.filter((event) => {
    const eventStart = new Date(event.startTime).getTime()
    return eventStart >= start && eventStart < end
  })
})

const sortedEvents = computed(() => [...props.events].sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()))

const ganttStart = computed(() => startOfDay(props.selectedDate))
const ganttDays = computed(() =>
  Array.from({ length: 7 }, (_, index) => {
    const date = new Date(ganttStart.value)
    date.setDate(ganttStart.value.getDate() + index)
    return {
      date,
      key: dateKey(date),
      label: dateFormatter.format(date),
      offset: index / 7,
    }
  }),
)

const ganttGroups = computed(() => {
  const map = new Map<string, CalendarEvent[]>()
  sortedEvents.value.forEach((event) => {
    const group = event.project || (event.ownerUserId ? props.memberNames.get(event.ownerUserId) : '') || '未分组'
    const list = map.get(group) ?? []
    list.push(event)
    map.set(group, list)
  })
  return [...map.entries()].map(([name, events]) => ({ name, events }))
})

function handleSpaceChange(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value)
  if (Number.isFinite(value)) {
    emit('select-space', value)
  }
}

function updateFilter(key: keyof WorkspaceFilters, value: string) {
  emit('update-filter', key, value)
}

function selectCalendarDate(date: Date) {
  emit('select-date', date)
  if (date.getFullYear() !== props.monthCursor.getFullYear() || date.getMonth() !== props.monthCursor.getMonth()) {
    emit('set-month', date)
  }
}

function dropOnDate(date: Date) {
  if (dragEventId.value) {
    emit('drop-calendar', dragEventId.value, date)
    dragEventId.value = null
  }
}

function eventsForHour(hour: number) {
  return selectedDateEvents.value.filter((event) => {
    const start = new Date(event.startTime)
    return event.allDay ? hour === 0 : start.getHours() === hour
  })
}

function ganttStyle(event: CalendarEvent) {
  const start = new Date(event.startTime).getTime()
  const end = new Date(event.endTime).getTime()
  const rangeStart = ganttStart.value.getTime()
  const dayMs = 24 * 60 * 60 * 1000
  const left = Math.max(0, ((start - rangeStart) / dayMs / 7) * 100)
  const width = Math.min(100 - left, Math.max(8, ((end - start) / dayMs / 7) * 100))
  return {
    left: `${left}%`,
    width: `${width}%`,
  }
}

function startOfDay(date: Date) {
  const copy = new Date(date)
  copy.setHours(0, 0, 0, 0)
  return copy
}

function sameDay(left: Date, right: Date) {
  return dateKey(left) === dateKey(right)
}

function dateKey(date: Date) {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
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

function formatHour(hour: number) {
  if (hour === 0) {
    return '12 AM'
  }
  if (hour === 12) {
    return '12 PM'
  }
  return hour > 12 ? `${hour - 12} PM` : `${hour} AM`
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    todo: '待处理',
    in_progress: '进行中',
    done: '已完成',
    blocked: '阻塞',
  }
  return labels[status] ?? status
}

function priorityLabel(priority: string) {
  const labels: Record<string, string> = {
    high: '高',
    medium: '中',
    low: '低',
  }
  return labels[priority] ?? priority
}
</script>
