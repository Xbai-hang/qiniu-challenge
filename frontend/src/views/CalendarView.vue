<template>
  <main class="ai-workbench">
    <AssistantPane
      :current-space="currentSpace"
      :events="events"
      :is-loading="isEventsLoading"
      :conflict-count="conflictIds.size"
      @create="openCreateDialog"
      @refresh="loadEvents"
    />

    <CalendarWorkspace
      :spaces="workspace.state.spaces"
      :selected-space-id="workspace.state.selectedSpaceId"
      :current-space="currentSpace"
      :events="events"
      :selected-date="selectedDate"
      :month-cursor="monthCursor"
      :view-mode="viewMode"
      :filters="filters"
      :conflict-ids="conflictIds"
      :member-names="memberNames"
      :is-loading="isEventsLoading"
      @select-space="selectSpace"
      @prev-month="shiftMonth(-1)"
      @next-month="shiftMonth(1)"
      @today="goToday"
      @select-date="selectDate"
      @create="openCreateDialog"
      @edit="openEditDialog"
      @drop-calendar="moveEventToDate"
      @change-view="viewMode = $event"
      @set-month="setMonth"
      @update-filter="updateFilter"
      @load="loadEvents"
    />

    <InsightPane :events="events" :conflict-ids="conflictIds" @edit="openEditDialog" />

    <ElDialog
      v-model="isEditorOpen"
      :title="editingEvent ? '编辑日程' : '创建日程'"
      width="760px"
      class="event-dialog"
      destroy-on-close
    >
      <form class="event-dialog-form" @submit.prevent="submitEvent">
        <label class="field">
          <span>标题</span>
          <input v-model.trim="form.title" required maxlength="200" autocomplete="off" placeholder="例如：项目复盘会" />
        </label>

        <div class="two-column-fields">
          <label class="field">
            <span>开始时间</span>
            <input v-model="form.startTime" type="datetime-local" required autocomplete="off" />
          </label>
          <label class="field">
            <span>结束时间</span>
            <input v-model="form.endTime" type="datetime-local" required autocomplete="off" />
          </label>
        </div>

        <div class="two-column-fields">
          <label class="field">
            <span>地点</span>
            <input v-model.trim="form.location" maxlength="200" autocomplete="off" placeholder="会议室 A / 线上" />
          </label>
          <label class="field">
            <span>负责人</span>
            <select v-model.number="form.ownerUserId" autocomplete="off">
              <option :value="null">未设置</option>
              <option v-for="member in selectableMembers" :key="member.userId" :value="member.userId">
                {{ memberLabel(member) }}
              </option>
            </select>
          </label>
        </div>

        <label class="field">
          <span>描述</span>
          <textarea v-model.trim="form.description" rows="3" autocomplete="off" placeholder="补充背景、议程或目标"></textarea>
        </label>

        <div class="two-column-fields">
          <label class="field">
            <span>项目</span>
            <input v-model.trim="form.project" maxlength="128" autocomplete="off" placeholder="项目名" />
          </label>
          <label class="field">
            <span>状态</span>
            <select v-model="form.status" autocomplete="off">
              <option value="">未设置</option>
              <option value="todo">待处理</option>
              <option value="in_progress">进行中</option>
              <option value="done">已完成</option>
              <option value="blocked">阻塞</option>
            </select>
          </label>
        </div>

        <div class="two-column-fields">
          <label class="field">
            <span>优先级</span>
            <select v-model="form.priority" autocomplete="off">
              <option value="">未设置</option>
              <option value="high">高</option>
              <option value="medium">中</option>
              <option value="low">低</option>
            </select>
          </label>
          <label class="field">
            <span>标签</span>
            <input v-model.trim="form.tagsText" autocomplete="off" placeholder="逗号分隔，例如：评审, 发布" />
          </label>
        </div>

        <label class="field">
          <span>参与人</span>
          <select v-if="selectableMembers.length > 0" v-model="form.participantUserIds" multiple class="participant-select">
            <option v-for="member in selectableMembers" :key="member.userId" :value="member.userId">
              {{ memberLabel(member) }}
            </option>
          </select>
          <div v-else class="readonly-space">
            <span class="space-dot" aria-hidden="true"></span>
            <strong>{{ auth.state.user?.displayName || auth.state.user?.username || '当前用户' }}</strong>
          </div>
        </label>

        <div v-if="isOrganizationSpace && organizationMembers.length > 0" class="member-strip">
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
          <textarea v-model.trim="form.notes" rows="3" autocomplete="off" placeholder="内部备注或后续动作"></textarea>
        </label>
      </form>

      <template #footer>
        <div class="dialog-actions">
          <button v-if="editingEvent" type="button" class="secondary-action danger-text" @click="confirmDelete(editingEvent)">
            <Delete />
            <span>删除</span>
          </button>
          <span class="dialog-spacer"></span>
          <button type="button" class="secondary-action" @click="isEditorOpen = false">取消</button>
          <button type="button" class="primary-action" :disabled="isSubmitting || !currentSpace" @click="submitEvent">
            <Check />
            <span>{{ editingEvent ? '保存修改' : '创建事件' }}</span>
          </button>
        </div>
      </template>
    </ElDialog>
  </main>
</template>

<script setup lang="ts">
import { Check, Delete } from '@element-plus/icons-vue'
import { ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  ApiClientError,
  checkEventConflicts,
  createCalendarEvent,
  deleteCalendarEvent,
  getCalendarEvents,
  getOrganizationMembers,
  updateCalendarEvent,
  type CalendarEvent,
  type EventConflict,
  type EventPayload,
  type OrganizationMember,
} from '../api'
import AssistantPane from '../components/workbench/AssistantPane.vue'
import CalendarWorkspace, {
  type WorkspaceFilters,
  type WorkspaceViewMode,
} from '../components/workbench/CalendarWorkspace.vue'
import InsightPane from '../components/workbench/InsightPane.vue'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'

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

type ConflictErrorDetails = {
  requiresConfirmation?: boolean
  conflicts?: EventConflict[]
}

const auth = useAuthStore()
const workspace = useWorkspaceStore()

const events = ref<CalendarEvent[]>([])
const organizationMembers = ref<OrganizationMember[]>([])
const selectedDate = ref(startOfDay(new Date()))
const monthCursor = ref(startOfMonth(new Date()))
const viewMode = ref<WorkspaceViewMode>('month')
const editingEvent = ref<CalendarEvent | null>(null)
const isEditorOpen = ref(false)
const isEventsLoading = ref(false)
const isSubmitting = ref(false)

const filters = reactive<WorkspaceFilters>({
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

const memberNames = computed(() => {
  const map = new Map<number, string>()
  selectableMembers.value.forEach((member) => {
    map.set(member.userId, memberLabel(member))
  })
  return map
})

const conflictIds = computed(() => {
  const ids = new Set<number>()
  events.value.forEach((event) => {
    if (event.requiresConfirmation || event.conflicts.length > 0) {
      ids.add(event.id)
    }
  })

  for (let index = 0; index < events.value.length; index += 1) {
    for (let compare = index + 1; compare < events.value.length; compare += 1) {
      const left = events.value[index]
      const right = events.value[compare]
      if (left.calendarSpaceId !== right.calendarSpaceId) {
        continue
      }
      if (eventsOverlap(left, right) && shareParticipant(left, right)) {
        ids.add(left.id)
        ids.add(right.id)
      }
    }
  }

  return ids
})

async function loadEvents() {
  if (!currentSpace.value) {
    events.value = []
    return
  }

  isEventsLoading.value = true
  try {
    const range = monthQueryRange(monthCursor.value)
    events.value = await getCalendarEvents(
      {
        calendarSpaceId: currentSpace.value.id,
        start: range.start,
        end: range.end,
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
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '事件加载失败')
    events.value = []
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

async function selectSpace(spaceId: number) {
  workspace.selectSpace(spaceId)
  resetForm()
  await loadMembers()
  await loadEvents()
}

function shiftMonth(offset: number) {
  const next = new Date(monthCursor.value)
  next.setMonth(next.getMonth() + offset)
  monthCursor.value = startOfMonth(next)
}

function setMonth(date: Date) {
  monthCursor.value = startOfMonth(date)
}

function goToday() {
  const today = new Date()
  selectedDate.value = startOfDay(today)
  monthCursor.value = startOfMonth(today)
}

function selectDate(date: Date) {
  selectedDate.value = startOfDay(date)
}

function updateFilter(key: keyof WorkspaceFilters, value: string) {
  filters[key] = value as never
}

function openCreateDialog() {
  resetForm(selectedDate.value)
  editingEvent.value = null
  isEditorOpen.value = true
}

function openEditDialog(event: CalendarEvent) {
  editingEvent.value = event
  Object.assign(form, formFromEvent(event))
  isEditorOpen.value = true
}

async function submitEvent() {
  if (!currentSpace.value) {
    ElMessage.warning('请先选择日历空间')
    return
  }

  isSubmitting.value = true
  try {
    const payload = buildEventPayload()
    const confirmed = await confirmConflictsIfNeeded(payload)
    if (!confirmed) {
      return
    }

    const saved = await saveEventPayload(payload)
    if (!saved) {
      return
    }

    ElMessage.success(editingEvent.value ? '事件已更新' : '事件已创建')
    isEditorOpen.value = false
    resetForm()
    await loadEvents()
  } finally {
    isSubmitting.value = false
  }
}

async function moveEventToDate(eventId: number, date: Date) {
  const event = events.value.find((item) => item.id === eventId)
  if (!event) {
    return
  }

  const start = new Date(event.startTime)
  const end = new Date(event.endTime)
  const duration = end.getTime() - start.getTime()
  const nextStart = new Date(date)
  nextStart.setHours(start.getHours(), start.getMinutes(), 0, 0)
  const nextEnd = new Date(nextStart.getTime() + duration)

  editingEvent.value = event
  const payload: EventPayload = {
    calendarSpaceId: event.calendarSpaceId,
    title: event.title,
    startTime: nextStart.toISOString(),
    endTime: nextEnd.toISOString(),
    location: event.location ?? '',
    description: event.description ?? '',
    notes: event.notes ?? '',
    version: event.version,
    participantUserIds: event.participants
      .filter((participant) => participant.role !== 'organizer')
      .map((participant) => participant.userId),
    enterpriseFields: {
      project: event.project ?? '',
      ownerUserId: event.ownerUserId ?? undefined,
      status: event.status ?? '',
      priority: event.priority ?? '',
      tags: event.tags,
    },
  }

  const confirmed = await confirmConflictsIfNeeded(payload)
  if (!confirmed) {
    editingEvent.value = null
    return
  }
  const saved = await saveEventPayload(payload)
  editingEvent.value = null
  if (saved) {
    ElMessage.success('事件日期已更新')
    selectedDate.value = startOfDay(date)
    await loadEvents()
  }
}

async function saveEventPayload(payload: EventPayload) {
  try {
    if (editingEvent.value) {
      await updateCalendarEvent(editingEvent.value.id, payload, { showErrorMessage: false })
    } else {
      await createCalendarEvent(payload, { showErrorMessage: false })
    }
    return true
  } catch (error) {
    if (!isConflictConfirmationError(error)) {
      ElMessage.error(error instanceof Error ? error.message : '事件保存失败')
      throw error
    }

    const confirmed = await confirmConflictList(error.details.conflicts ?? [])
    if (!confirmed) {
      return false
    }

    if (editingEvent.value) {
      await updateCalendarEvent(
        editingEvent.value.id,
        { ...payload, forceUpdateOnConflict: true },
        { showErrorMessage: false },
      )
    } else {
      await createCalendarEvent({ ...payload, forceCreateOnConflict: true }, { showErrorMessage: false })
    }
    return true
  }
}

async function confirmConflictsIfNeeded(payload: EventPayload) {
  if (!payload.calendarSpaceId || !payload.startTime || !payload.endTime) {
    return true
  }

  const result = await checkEventConflicts({
    calendarSpaceId: payload.calendarSpaceId,
    eventId: editingEvent.value?.id,
    participantUserIds: conflictParticipantUserIds(payload),
    startTime: payload.startTime,
    endTime: payload.endTime,
  })

  if (!result.hasConflict) {
    return true
  }

  const confirmed = await confirmConflictList(result.conflicts)
  if (confirmed) {
    if (editingEvent.value) {
      payload.forceUpdateOnConflict = true
    } else {
      payload.forceCreateOnConflict = true
    }
  }
  return confirmed
}

async function confirmConflictList(conflicts: EventConflict[]) {
  try {
    await ElMessageBox.confirm(conflictSummary(conflicts), '检测到日程冲突', {
      confirmButtonText: editingEvent.value ? '仍然保存' : '仍然创建',
      cancelButtonText: '返回修改',
      type: 'warning',
      dangerouslyUseHTMLString: true,
    })
    return true
  } catch {
    return false
  }
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
  isEditorOpen.value = false
  resetForm()
  await loadEvents()
}

function buildEventPayload(): EventPayload {
  return {
    calendarSpaceId: currentSpace.value?.id,
    title: form.title,
    startTime: toApiDate(form.startTime),
    endTime: toApiDate(form.endTime),
    location: form.location,
    description: form.description,
    notes: form.notes,
    version: editingEvent.value?.version,
    participantUserIds: form.participantUserIds,
    enterpriseFields: {
      project: form.project,
      ownerUserId: form.ownerUserId || undefined,
      status: form.status,
      priority: form.priority,
      tags: parseTextList(form.tagsText),
    },
  }
}

function resetForm(date = new Date()) {
  editingEvent.value = null
  Object.assign(form, emptyForm(date))
}

function emptyForm(date = new Date()): EventFormState {
  const start = new Date(date)
  start.setMinutes(0, 0, 0)
  if (start.getTime() < Date.now() - 60 * 60 * 1000) {
    const now = new Date()
    start.setHours(now.getHours() + 1, 0, 0, 0)
  }
  const end = new Date(start)
  end.setHours(end.getHours() + 1)

  return {
    title: '',
    startTime: toLocalInputValue(start.toISOString()),
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

function formFromEvent(event: CalendarEvent): EventFormState {
  return {
    title: event.title,
    startTime: toLocalInputValue(event.startTime),
    endTime: toLocalInputValue(event.endTime),
    location: event.location ?? '',
    description: event.description ?? '',
    project: event.project ?? '',
    ownerUserId: event.ownerUserId ?? null,
    status: event.status ?? '',
    priority: event.priority ?? '',
    tagsText: event.tags.join(', '),
    participantUserIds: event.participants
      .filter((participant) => participant.role !== 'organizer')
      .map((participant) => participant.userId),
    notes: event.notes ?? '',
  }
}

function toggleParticipant(userId: number) {
  if (form.participantUserIds.includes(userId)) {
    form.participantUserIds = form.participantUserIds.filter((id) => id !== userId)
  } else {
    form.participantUserIds = [...form.participantUserIds, userId]
  }
}

function conflictParticipantUserIds(payload: EventPayload) {
  const participantIds = new Set<number>()
  if (editingEvent.value) {
    editingEvent.value.participants
      .filter((participant) => participant.role === 'organizer')
      .forEach((participant) => participantIds.add(participant.userId))
  } else if (auth.state.user?.id) {
    participantIds.add(auth.state.user.id)
  }
  payload.participantUserIds?.forEach((userId) => participantIds.add(userId))
  return [...participantIds]
}

function isConflictConfirmationError(error: unknown): error is ApiClientError & { details: ConflictErrorDetails } {
  if (!(error instanceof ApiClientError) || error.code !== 'CONFLICT') {
    return false
  }
  const details = error.details as ConflictErrorDetails | undefined
  return Boolean(details?.requiresConfirmation && Array.isArray(details.conflicts))
}

function eventsOverlap(left: CalendarEvent, right: CalendarEvent) {
  return new Date(left.startTime).getTime() < new Date(right.endTime).getTime()
    && new Date(right.startTime).getTime() < new Date(left.endTime).getTime()
}

function shareParticipant(left: CalendarEvent, right: CalendarEvent) {
  const leftIds = new Set(left.participants.map((participant) => participant.userId))
  if (leftIds.size === 0) {
    leftIds.add(left.createdBy)
  }
  const rightIds = right.participants.map((participant) => participant.userId)
  if (rightIds.length === 0) {
    rightIds.push(right.createdBy)
  }
  return rightIds.some((id) => leftIds.has(id))
}

function monthQueryRange(date: Date) {
  const start = startOfMonth(date)
  start.setDate(start.getDate() - 7)
  const end = startOfMonth(date)
  end.setMonth(end.getMonth() + 1)
  end.setDate(end.getDate() + 7)
  return {
    start: start.toISOString(),
    end: end.toISOString(),
  }
}

function startOfDay(date: Date) {
  const copy = new Date(date)
  copy.setHours(0, 0, 0, 0)
  return copy
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
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

function memberLabel(member: OrganizationMember) {
  return member.nickname || member.displayName || `用户 ${member.userId}`
}

function conflictSummary(conflicts: EventConflict[]) {
  const items = conflicts
    .slice(0, 6)
    .map(
      (conflict) => `
      <li>
        <strong>${escapeHtml(conflict.participantName)} 已有安排</strong>
        <em>${escapeHtml(formatTimeRange(conflict.startTime, conflict.endTime))}</em>
      </li>
    `,
    )
    .join('')
  const more = conflicts.length > 6 ? `<p>另有 ${conflicts.length - 6} 个冲突未展示。</p>` : ''

  return `
    <div class="conflict-confirm">
      <p>该时间段已有日程安排。继续操作会保留冲突，请确认是否继续。</p>
      <ul>${items}</ul>
      ${more}
    </div>
  `
}

function formatTimeRange(start: string, end: string) {
  const formatter = new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
  return `${formatter.format(new Date(start))} - ${formatter.format(new Date(end))}`
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

watch(
  () => [monthCursor.value.getTime(), filters.keyword, filters.project, filters.status, filters.priority, filters.tag],
  () => {
    void loadEvents()
  },
)

watch(
  () => workspace.state.selectedSpaceId,
  () => {
    void loadMembers().then(loadEvents)
  },
)

function handleAiCalendarUpdated() {
  void loadEvents()
}

onMounted(async () => {
  window.addEventListener('ai-calendar-updated', handleAiCalendarUpdated)
  await auth.restoreSession()
  await workspace.loadSpaces()
  await loadMembers()
  await loadEvents()
})

onBeforeUnmount(() => {
  window.removeEventListener('ai-calendar-updated', handleAiCalendarUpdated)
})
</script>
