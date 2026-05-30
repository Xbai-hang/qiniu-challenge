<template>
  <main class="calendar-workbench">
    <section class="calendar-toolbar panel" aria-labelledby="calendar-title">
      <div class="calendar-title-block">
        <p class="eyebrow">Calendar Operations</p>
        <h1 id="calendar-title">事件工作台</h1>
        <p class="summary">按空间、时间和企业字段管理日历事件。</p>
      </div>

      <form class="calendar-filters" aria-label="事件筛选" @submit.prevent="loadEvents">
        <div class="field compact-field">
          <span>空间</span>
          <div :class="['readonly-space', currentSpace?.type === 'organization' ? 'is-organization' : 'is-personal']">
            <span class="space-dot" aria-hidden="true"></span>
            <strong>{{ currentSpace?.name || '暂无空间' }}</strong>
          </div>
        </div>

        <label class="field compact-field">
          <span>开始</span>
          <input v-model="filters.start" name="start" type="datetime-local" autocomplete="off" />
        </label>

        <label class="field compact-field">
          <span>结束</span>
          <input v-model="filters.end" name="end" type="datetime-local" autocomplete="off" />
        </label>

        <label class="field compact-field">
          <span>关键词</span>
          <input
            v-model.trim="filters.keyword"
            name="keyword"
            type="search"
            autocomplete="off"
            placeholder="标题、备注、项目…"
          />
        </label>

        <label class="field compact-field">
          <span>项目</span>
          <input v-model.trim="filters.project" name="project" type="text" autocomplete="off" placeholder="项目名…" />
        </label>

        <label class="field compact-field">
          <span>状态</span>
          <select v-model="filters.status" name="status" autocomplete="off">
            <option value="">全部</option>
            <option value="todo">待处理</option>
            <option value="in_progress">进行中</option>
            <option value="done">已完成</option>
            <option value="blocked">阻塞</option>
          </select>
        </label>

        <label class="field compact-field">
          <span>优先级</span>
          <select v-model="filters.priority" name="priority" autocomplete="off">
            <option value="">全部</option>
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </label>

        <label class="field compact-field">
          <span>标签</span>
          <input v-model.trim="filters.tag" name="tag" type="text" autocomplete="off" placeholder="标签…" />
        </label>

        <label class="field compact-field">
          <span>排序</span>
          <select v-model="filters.sortBy" name="sortBy" autocomplete="off">
            <option value="startTime">开始时间</option>
            <option value="endTime">结束时间</option>
            <option value="title">标题</option>
            <option value="project">项目</option>
            <option value="status">状态</option>
            <option value="priority">优先级</option>
          </select>
        </label>

        <label class="field compact-field">
          <span>方向</span>
          <select v-model="filters.sortDirection" name="sortDirection" autocomplete="off">
            <option value="asc">升序</option>
            <option value="desc">降序</option>
          </select>
        </label>

        <div class="filter-actions">
          <button type="submit" class="primary-action" :disabled="isEventsLoading || !currentSpace">
            <Search />
            <span>查询</span>
          </button>
          <button type="button" class="secondary-action" @click="resetFilters">
            <RefreshLeft />
            <span>重置</span>
          </button>
        </div>
      </form>
    </section>

    <section class="event-shell">
      <aside class="panel event-editor" aria-labelledby="event-form-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Editor</p>
            <h2 id="event-form-title">{{ editingEvent ? '编辑事件' : '创建事件' }}</h2>
          </div>
          <button v-if="editingEvent" type="button" class="icon-button" aria-label="取消编辑" @click="resetForm">
            <Close />
          </button>
        </div>

        <form class="event-form" @submit.prevent="submitEvent">
          <label class="field">
            <span>标题</span>
            <input
              v-model.trim="form.title"
              name="title"
              type="text"
              required
              maxlength="200"
              autocomplete="off"
              placeholder="例如：项目复盘会…"
            />
          </label>

          <div class="two-column-fields">
            <label class="field">
              <span>开始时间</span>
              <input v-model="form.startTime" name="eventStartTime" type="datetime-local" autocomplete="off" required />
            </label>
            <label class="field">
              <span>结束时间</span>
              <input v-model="form.endTime" name="eventEndTime" type="datetime-local" autocomplete="off" required />
            </label>
          </div>

          <label class="field">
            <span>地点</span>
            <input
              v-model.trim="form.location"
              name="location"
              type="text"
              maxlength="200"
              autocomplete="off"
              placeholder="会议室 A / 线上…"
            />
          </label>

          <label class="field">
            <span>描述</span>
            <textarea
              v-model.trim="form.description"
              name="description"
              rows="3"
              autocomplete="off"
              placeholder="补充背景、议程或目标…"
            ></textarea>
          </label>

          <div class="two-column-fields">
            <label class="field">
              <span>项目</span>
              <input
                v-model.trim="form.project"
                name="eventProject"
                type="text"
                maxlength="128"
                autocomplete="off"
                placeholder="项目名…"
              />
            </label>
            <label class="field">
              <span>负责人</span>
              <select
                v-model.number="form.ownerUserId"
                name="ownerUserId"
                autocomplete="off"
              >
                <option :value="null">未设置</option>
                <option v-for="member in selectableMembers" :key="member.userId" :value="member.userId">
                  {{ memberLabel(member) }}
                </option>
              </select>
            </label>
          </div>

          <div class="two-column-fields">
            <label class="field">
              <span>状态</span>
              <select v-model="form.status" name="eventStatus" autocomplete="off">
                <option value="">未设置</option>
                <option value="todo">待处理</option>
                <option value="in_progress">进行中</option>
                <option value="done">已完成</option>
                <option value="blocked">阻塞</option>
              </select>
            </label>
            <label class="field">
              <span>优先级</span>
              <select v-model="form.priority" name="eventPriority" autocomplete="off">
                <option value="">未设置</option>
                <option value="high">高</option>
                <option value="medium">中</option>
                <option value="low">低</option>
              </select>
            </label>
          </div>

          <label class="field">
            <span>标签</span>
            <input
              v-model.trim="form.tagsText"
              name="tags"
              type="text"
              autocomplete="off"
              placeholder="逗号分隔，例如：周会, 发布…"
            />
          </label>

          <label class="field">
            <span>参与人</span>
            <select
              v-if="selectableMembers.length > 0"
              v-model="form.participantUserIds"
              name="participantUserIds"
              multiple
              autocomplete="off"
              class="participant-select"
            >
              <option v-for="member in selectableMembers" :key="member.userId" :value="member.userId">
                {{ memberLabel(member) }}
              </option>
            </select>
            <div v-else class="readonly-space">
              <span class="space-dot" aria-hidden="true"></span>
              <strong>{{ auth.state.user?.displayName || auth.state.user?.username || '当前用户' }}</strong>
            </div>
          </label>

          <div v-if="isOrganizationSpace && organizationMembers.length > 0" class="member-strip" aria-label="组织成员">
            <button
              v-for="member in organizationMembers"
              :key="member.userId"
              type="button"
              :class="['member-chip', form.participantUserIds.includes(member.userId) ? 'is-selected' : '']"
              @click="toggleParticipant(member.userId)"
            >
              {{ memberLabel(member) }}
            </button>
          </div>

          <label class="field">
            <span>备注</span>
            <textarea
              v-model.trim="form.notes"
              name="notes"
              rows="3"
              autocomplete="off"
              placeholder="内部备注或后续动作…"
            ></textarea>
          </label>

          <div class="form-actions">
            <button type="submit" class="primary-action" :disabled="isSubmitting || !currentSpace">
              <Check />
              <span>{{ editingEvent ? '保存修改' : '创建事件' }}</span>
            </button>
            <button type="button" class="secondary-action" @click="resetForm">
              <RefreshLeft />
              <span>清空</span>
            </button>
          </div>
        </form>
      </aside>

      <section class="panel event-list-panel" aria-labelledby="event-list-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Events</p>
            <h2 id="event-list-title">事件列表</h2>
          </div>
          <span class="status">{{ events.length }} 条</span>
        </div>

        <div v-if="isEventsLoading" class="empty-state" role="status" aria-live="polite">正在加载事件…</div>
        <div v-else-if="events.length === 0" class="empty-state">当前筛选范围内没有事件。</div>

        <article v-for="event in events" v-else :key="event.id" class="event-row">
          <div class="event-time">
            <strong>{{ formatTime(event.startTime) }}</strong>
            <span>{{ formatDate(event.startTime) }}</span>
          </div>

          <div class="event-main">
            <div class="event-title-line">
              <h3>{{ event.title }}</h3>
              <span v-if="event.priority" :class="['priority-pill', `is-${event.priority}`]">
                {{ priorityLabel(event.priority) }}
              </span>
            </div>
            <p>{{ event.description || event.notes || '无补充说明' }}</p>
            <div class="event-meta">
              <span v-if="event.project">{{ event.project }}</span>
              <span v-if="event.status">{{ statusLabel(event.status) }}</span>
              <span v-if="event.location">{{ event.location }}</span>
              <span>{{ formatTimeRange(event.startTime, event.endTime) }}</span>
            </div>
            <div v-if="event.tags.length > 0" class="tag-row">
              <span v-for="tag in event.tags" :key="tag">#{{ tag }}</span>
            </div>
          </div>

          <div class="event-actions">
            <button type="button" class="icon-button" :aria-label="`编辑 ${event.title}`" @click="editEvent(event)">
              <Edit />
            </button>
            <button
              type="button"
              class="icon-button danger-button"
              :aria-label="`删除 ${event.title}`"
              @click="confirmDelete(event)"
            >
              <Delete />
            </button>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Check, Close, Delete, Edit, RefreshLeft, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createCalendarEvent,
  deleteCalendarEvent,
  getCalendarEvents,
  getOrganizationMembers,
  updateCalendarEvent,
  type CalendarEvent,
  type OrganizationMember,
} from '../api'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'

type FilterState = {
  start: string
  end: string
  keyword: string
  project: string
  status: string
  priority: string
  tag: string
  sortBy: string
  sortDirection: 'asc' | 'desc'
}

type EventFormState = {
  title: string
  startTime: string
  endTime: string
  location: string
  description: string
  project: string
  ownerUserId: number | null
  status: string
  priority: string
  tagsText: string
  participantUserIds: number[]
  notes: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const workspace = useWorkspaceStore()

const organizationMembers = ref<OrganizationMember[]>([])
const events = ref<CalendarEvent[]>([])
const editingEvent = ref<CalendarEvent | null>(null)
const isEventsLoading = ref(false)
const isSubmitting = ref(false)
const isSyncingRoute = ref(false)

const filters = reactive<FilterState>({
  start: '',
  end: '',
  keyword: '',
  project: '',
  status: '',
  priority: '',
  tag: '',
  sortBy: 'startTime',
  sortDirection: 'asc',
})

const form = reactive<EventFormState>(emptyForm())

const currentSpace = computed(() => workspace.currentSpace.value)
const isOrganizationSpace = computed(() => currentSpace.value?.type === 'organization')
const selectableMembers = computed(() => {
  if (isOrganizationSpace.value) {
    return organizationMembers.value
  }

  const user = auth.state.user
  return user
    ? [
        {
          userId: user.id,
          displayName: user.displayName,
          nickname: user.displayName,
          role: 'owner',
          status: 'active',
        } satisfies OrganizationMember,
      ]
    : []
})

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  weekday: 'short',
})

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
})

async function loadEvents() {
  if (!currentSpace.value) {
    events.value = []
    return
  }

  isEventsLoading.value = true
  syncRouteFromFilters()

  try {
    events.value = await getCalendarEvents(
      {
        calendarSpaceId: currentSpace.value.id,
        start: toApiDate(filters.start),
        end: toApiDate(filters.end),
        keyword: filters.keyword,
        project: filters.project,
        status: filters.status,
        priority: filters.priority,
        tag: filters.tag,
        sortBy: filters.sortBy,
        sortDirection: filters.sortDirection,
      },
      { showErrorMessage: false },
    )
  } finally {
    isEventsLoading.value = false
  }
}

async function loadMembers() {
  organizationMembers.value = []
  if (!currentSpace.value?.organizationId) {
    return
  }

  organizationMembers.value = await getOrganizationMembers(currentSpace.value.organizationId, {
    showErrorMessage: false,
  })
}

async function submitEvent() {
  if (!currentSpace.value) {
    ElMessage.warning('请先在导航栏选择日历空间')
    return
  }

  isSubmitting.value = true
  try {
    const payload = {
      calendarSpaceId: currentSpace.value.id,
      title: form.title,
      startTime: toApiDate(form.startTime),
      endTime: toApiDate(form.endTime),
      location: form.location,
      description: form.description,
      notes: form.notes,
      participantUserIds: form.participantUserIds,
      enterpriseFields: {
        project: form.project,
        ownerUserId: form.ownerUserId || undefined,
        status: form.status,
        priority: form.priority,
        tags: parseTextList(form.tagsText),
      },
    }

    if (editingEvent.value) {
      await updateCalendarEvent(editingEvent.value.id, {
        ...payload,
        version: editingEvent.value.version,
      })
      ElMessage.success('事件已更新')
    } else {
      await createCalendarEvent(payload)
      ElMessage.success('事件已创建')
    }

    resetForm()
    await loadEvents()
  } finally {
    isSubmitting.value = false
  }
}

function editEvent(event: CalendarEvent) {
  editingEvent.value = event
  form.title = event.title
  form.startTime = toLocalInputValue(event.startTime)
  form.endTime = toLocalInputValue(event.endTime)
  form.location = event.location ?? ''
  form.description = event.description ?? ''
  form.project = event.project ?? ''
  form.ownerUserId = event.ownerUserId ?? null
  form.status = event.status ?? ''
  form.priority = event.priority ?? ''
  form.tagsText = event.tags.join(', ')
  form.participantUserIds = event.participants
    .filter((participant) => participant.role !== 'organizer')
    .map((participant) => participant.userId)
  form.notes = event.notes ?? ''
}

async function confirmDelete(event: CalendarEvent) {
  try {
    await ElMessageBox.confirm(`确定删除「${event.title}」吗？删除后列表中将不再展示。`, '删除事件', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await deleteCalendarEvent(event.id)
  ElMessage.success('事件已删除')
  if (editingEvent.value?.id === event.id) {
    resetForm()
  }
  await loadEvents()
}

function resetForm() {
  editingEvent.value = null
  Object.assign(form, emptyForm())
}

function resetFilters() {
  const defaults = defaultDateRange()
  Object.assign(filters, {
    start: defaults.start,
    end: defaults.end,
    keyword: '',
    project: '',
    status: '',
    priority: '',
    tag: '',
    sortBy: 'startTime',
    sortDirection: 'asc',
  })
  void loadEvents()
}

function toggleParticipant(userId: number) {
  if (form.participantUserIds.includes(userId)) {
    form.participantUserIds = form.participantUserIds.filter((id) => id !== userId)
  } else {
    form.participantUserIds = [...form.participantUserIds, userId]
  }
}

function syncFiltersFromRoute() {
  const query = route.query
  const defaults = defaultDateRange()
  filters.start = typeof query.start === 'string' ? query.start : defaults.start
  filters.end = typeof query.end === 'string' ? query.end : defaults.end
  filters.keyword = typeof query.keyword === 'string' ? query.keyword : ''
  filters.project = typeof query.project === 'string' ? query.project : ''
  filters.status = typeof query.status === 'string' ? query.status : ''
  filters.priority = typeof query.priority === 'string' ? query.priority : ''
  filters.tag = typeof query.tag === 'string' ? query.tag : ''
  filters.sortBy = typeof query.sortBy === 'string' ? query.sortBy : 'startTime'
  filters.sortDirection = query.sortDirection === 'desc' ? 'desc' : 'asc'
}

function syncRouteFromFilters() {
  isSyncingRoute.value = true
  void router
    .replace({
      name: 'calendar',
      query: {
        start: filters.start || undefined,
        end: filters.end || undefined,
        keyword: filters.keyword || undefined,
        project: filters.project || undefined,
        status: filters.status || undefined,
        priority: filters.priority || undefined,
        tag: filters.tag || undefined,
        sortBy: filters.sortBy === 'startTime' ? undefined : filters.sortBy,
        sortDirection: filters.sortDirection === 'asc' ? undefined : filters.sortDirection,
      },
    })
    .finally(() => {
      isSyncingRoute.value = false
    })
}

function emptyForm(): EventFormState {
  const now = new Date()
  now.setMinutes(0, 0, 0)
  const end = new Date(now)
  end.setHours(end.getHours() + 1)

  return {
    title: '',
    startTime: toLocalInputValue(now.toISOString()),
    endTime: toLocalInputValue(end.toISOString()),
    location: '',
    description: '',
    project: '',
    ownerUserId: auth.state.user?.id ?? null,
    status: '',
    priority: '',
    tagsText: '',
    participantUserIds: [],
    notes: '',
  }
}

function defaultDateRange() {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const end = new Date(start)
  end.setDate(end.getDate() + 14)
  return {
    start: toLocalInputValue(start.toISOString()),
    end: toLocalInputValue(end.toISOString()),
  }
}

function parseTextList(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function toApiDate(value: string) {
  return value ? new Date(value).toISOString() : undefined
}

function toLocalInputValue(value: string) {
  const date = new Date(value)
  const offset = date.getTimezoneOffset()
  const local = new Date(date.getTime() - offset * 60_000)
  return local.toISOString().slice(0, 16)
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

function memberLabel(member: OrganizationMember) {
  return member.nickname || member.displayName || `用户 ${member.userId}`
}

watch(
  () => workspace.state.selectedSpaceId,
  async () => {
    await loadMembers()
    resetForm()
    await loadEvents()
  },
)

watch(
  () => route.query,
  () => {
    if (!isSyncingRoute.value) {
      syncFiltersFromRoute()
      void loadEvents()
    }
  },
)

onMounted(async () => {
  await auth.restoreSession()
  syncFiltersFromRoute()
  await workspace.loadSpaces()
  await loadMembers()
  await loadEvents()
})
</script>
