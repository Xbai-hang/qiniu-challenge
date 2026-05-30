<template>
  <main class="operation-log-page">
    <section class="panel audit-toolbar" aria-labelledby="audit-title">
      <div class="calendar-title-block">
        <p class="eyebrow">Audit Trail</p>
        <h1 id="audit-title">操作日志记录</h1>
        <p class="summary">查看手动、AI 和系统写操作，按空间、来源和目标类型筛选并导出。</p>
      </div>

      <form class="calendar-filters audit-filters" aria-label="操作日志筛选" @submit.prevent="loadLogs(1)">
        <label class="field compact-field">
          <span>空间</span>
          <select v-model="filters.calendarSpaceId" name="calendarSpaceId" autocomplete="off">
            <option :value="null">全部可审计空间</option>
            <option v-for="space in workspace.state.spaces" :key="space.id" :value="space.id">
              {{ space.name }} · {{ space.type === 'organization' ? '组织' : '个人' }}
            </option>
          </select>
        </label>

        <label class="field compact-field">
          <span>来源</span>
          <select v-model="filters.operationSource" name="operationSource" autocomplete="off">
            <option value="">全部</option>
            <option value="manual">手动</option>
            <option value="ai">AI</option>
            <option value="system">系统</option>
          </select>
        </label>

        <label class="field compact-field">
          <span>目标</span>
          <select v-model="filters.targetType" name="targetType" autocomplete="off">
            <option value="">全部</option>
            <option value="event">事件</option>
            <option value="reminder">提醒</option>
            <option value="notification">通知</option>
          </select>
        </label>

        <div class="filter-actions">
          <button type="submit" class="primary-action" :disabled="isLoading">
            <Search />
            <span>查询</span>
          </button>
          <button type="button" class="secondary-action" :disabled="isExporting" @click="downloadLogs">
            <Download />
            <span>导出</span>
          </button>
        </div>
      </form>
    </section>

    <section class="panel audit-list-panel" aria-labelledby="audit-list-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Records</p>
          <h2 id="audit-list-title">审计时间线</h2>
        </div>
        <span class="status">{{ page.total }} 条</span>
      </div>

      <div v-if="isLoading" class="empty-state" role="status" aria-live="polite">正在加载操作日志…</div>
      <div v-else-if="page.items.length === 0" class="empty-state">当前筛选条件下没有操作日志。</div>

      <div v-else class="audit-table-wrap">
        <table class="audit-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>操作者</th>
              <th>空间</th>
              <th>操作</th>
              <th>目标</th>
              <th>撤销</th>
              <th>快照</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in page.items" :key="log.id">
              <td>
                <strong>{{ formatDateTime(log.createdAt) }}</strong>
                <span>#{{ log.id }}</span>
              </td>
              <td>{{ log.userDisplayName || `用户 ${log.userId}` }}</td>
              <td>{{ log.calendarSpaceName }}</td>
              <td>
                <span :class="['audit-pill', `is-${log.operationSource}`]">{{ sourceLabel(log.operationSource) }}</span>
                <span>{{ operationLabel(log.operationType) }}</span>
              </td>
              <td>{{ targetLabel(log.targetType) }}{{ log.targetId ? ` #${log.targetId}` : '' }}</td>
              <td>{{ undoLabel(log) }}</td>
              <td>
                <button type="button" class="text-action" @click="openSnapshot(log)">查看</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="audit-pagination">
        <button type="button" class="secondary-action" :disabled="page.page <= 1 || isLoading" @click="loadLogs(page.page - 1)">
          上一页
        </button>
        <span>第 {{ page.page }} 页 / 共 {{ totalPages }} 页</span>
        <button type="button" class="secondary-action" :disabled="page.page >= totalPages || isLoading" @click="loadLogs(page.page + 1)">
          下一页
        </button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Download, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  exportOperationLogs,
  getOperationLogs,
  type OperationLogPage,
  type OperationLogRecord,
} from '../api'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'

const auth = useAuthStore()
const workspace = useWorkspaceStore()

const filters = reactive({
  calendarSpaceId: null as number | null,
  operationSource: '',
  targetType: '',
})

const page = ref<OperationLogPage>({
  items: [],
  page: 1,
  size: 20,
  total: 0,
})
const isLoading = ref(false)
const isExporting = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(page.value.total / page.value.size)))

async function loadLogs(nextPage = page.value.page) {
  isLoading.value = true
  try {
    page.value = await getOperationLogs(
      {
        calendarSpaceId: filters.calendarSpaceId,
        operationSource: filters.operationSource,
        targetType: filters.targetType,
        page: nextPage,
        size: page.value.size,
      },
      { showErrorMessage: false },
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作日志加载失败')
  } finally {
    isLoading.value = false
  }
}

async function downloadLogs() {
  isExporting.value = true
  try {
    await exportOperationLogs({
      calendarSpaceId: filters.calendarSpaceId,
      operationSource: filters.operationSource,
      targetType: filters.targetType,
    })
    ElMessage.success('操作日志已导出')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作日志导出失败')
  } finally {
    isExporting.value = false
  }
}

function openSnapshot(log: OperationLogRecord) {
  const before = prettyJson(log.beforeSnapshot)
  const after = prettyJson(log.afterSnapshot)
  void ElMessageBox.alert(
    `<div class="snapshot-dialog">
      <strong>Before</strong>
      <pre>${escapeHtml(before || '无')}</pre>
      <strong>After</strong>
      <pre>${escapeHtml(after || '无')}</pre>
    </div>`,
    `日志 #${log.id}`,
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '关闭',
    },
  )
}

function prettyJson(value?: string | null) {
  if (!value) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function sourceLabel(source: string) {
  const labels: Record<string, string> = {
    manual: '手动',
    ai: 'AI',
    system: '系统',
  }
  return labels[source] ?? source
}

function operationLabel(type: string) {
  const labels: Record<string, string> = {
    create: '创建',
    update: '修改',
    delete: '删除',
    snooze: '稍后提醒',
    undo: '撤销',
  }
  return labels[type] ?? type
}

function targetLabel(type: string) {
  const labels: Record<string, string> = {
    event: '事件',
    reminder: '提醒',
    notification: '通知',
  }
  return labels[type] ?? type
}

function undoLabel(log: OperationLogRecord) {
  if (log.undone) {
    return '已撤销'
  }
  if (log.undoable) {
    return log.undoExpiresAt ? `可撤销至 ${formatDateTime(log.undoExpiresAt)}` : '可撤销'
  }
  return '不可撤销'
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

onMounted(async () => {
  await auth.restoreSession()
  await workspace.loadSpaces()
  await loadLogs(1)
})
</script>
