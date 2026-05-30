<template>
  <main class="route-page settings-page">
    <section class="organization-hero">
      <div class="hero-copy">
        <p class="eyebrow">Organization Control</p>
        <h1>空间与权限中枢</h1>
      </div>

      <div class="hero-metrics" aria-label="组织概览">
        <div>
          <span>{{ organizations.length }}</span>
          <strong>组织</strong>
        </div>
        <div>
          <span>{{ members.length }}</span>
          <strong>成员</strong>
        </div>
        <div>
          <span>{{ selectedOrganization ? roleLabel(selectedOrganization.role) : '-' }}</span>
          <strong>当前角色</strong>
        </div>
      </div>
    </section>

    <section class="organization-console">
      <aside class="org-rail">
        <div class="rail-header">
          <div>
            <p class="eyebrow">Directory</p>
            <h2>我的组织</h2>
          </div>
          <button type="button" class="icon-tool" :disabled="isOrganizationsLoading" @click="loadOrganizations">
            <Refresh />
          </button>
        </div>

        <div v-if="isOrganizationsLoading" class="empty-state">正在同步组织</div>
        <div v-else-if="organizations.length === 0" class="empty-state">暂无组织</div>
        <button
          v-for="organization in organizations"
          v-else
          :key="organization.organizationId"
          type="button"
          :class="['org-item', { active: organization.organizationId === selectedOrganizationId }]"
          @click="selectedOrganizationId = organization.organizationId"
        >
          <span class="org-mark" aria-hidden="true">{{ organization.name.slice(0, 1) }}</span>
          <span>
            <strong>{{ organization.name }}</strong>
            <small>{{ roleLabel(organization.role) }} · Space #{{ organization.spaceId }}</small>
          </span>
        </button>

        <form class="rail-form" @submit.prevent="handleCreateOrganization">
          <label>
            <span>新组织</span>
            <input v-model.trim="createName" type="text" maxlength="128" placeholder="Alpha 团队" />
          </label>
          <button type="submit" class="solid-action" :disabled="isCreateLoading || !createName">
            {{ isCreateLoading ? '创建中' : '创建组织' }}
          </button>
        </form>

        <form class="rail-form join-form" @submit.prevent="handleJoinOrganization">
          <label>
            <span>邀请码</span>
            <input v-model.trim="joinCode" type="text" maxlength="64" placeholder="ALPHA123" />
          </label>
          <button type="submit" class="line-action" :disabled="isJoinLoading || !joinCode">
            {{ isJoinLoading ? '加入中' : '加入组织' }}
          </button>
        </form>
      </aside>

      <section class="org-workbench">
        <div v-if="!selectedOrganization" class="workbench-empty">
          <p class="eyebrow">No Organization</p>
          <h2>选择或创建组织</h2>
        </div>

        <template v-else>
          <header class="workbench-header">
            <div>
              <p class="eyebrow">Selected Organization</p>
              <h2>{{ selectedOrganization.name }}</h2>
            </div>
            <span :class="['role-pill', selectedOrganization.role]">
              {{ roleLabel(selectedOrganization.role) }}
            </span>
          </header>

          <section class="invite-strip">
            <div>
              <p class="eyebrow">Invite Code</p>
              <strong>{{ visibleInviteCode || '未显示' }}</strong>
            </div>
            <button
              type="button"
              class="solid-action compact"
              :disabled="!canRefreshInvite || isInviteLoading"
              @click="handleRefreshInviteCode"
            >
              {{ isInviteLoading ? '刷新中' : '刷新邀请码' }}
            </button>
          </section>

          <section class="member-panel">
            <div class="member-panel-head">
              <div>
                <p class="eyebrow">Members</p>
                <h2>成员权限</h2>
              </div>
              <button type="button" class="icon-tool" :disabled="isMembersLoading" @click="loadMembers">
                <Refresh />
              </button>
            </div>

            <div v-if="isMembersLoading" class="empty-state">正在加载成员</div>
            <div v-else-if="members.length === 0" class="empty-state">暂无成员</div>
            <div v-else class="member-table">
              <div class="member-row table-head">
                <span>成员</span>
                <span>职务</span>
                <span>角色</span>
                <span>操作</span>
              </div>

              <div v-for="member in members" :key="member.userId" class="member-row">
                <div class="member-cell">
                  <span class="member-avatar" aria-hidden="true">{{ member.displayName.slice(0, 1) }}</span>
                  <span>
                    <strong>{{ member.displayName }}</strong>
                    <small>#{{ member.userId }} · {{ member.nickname }}</small>
                  </span>
                </div>
                <span class="muted">{{ member.title || '未设置' }}</span>
                <span :class="['role-pill', member.role]">{{ roleLabel(member.role) }}</span>
                <div class="member-actions">
                  <select
                    :value="member.role"
                    :disabled="!canEditRole(member)"
                    aria-label="成员角色"
                    @change="handleRoleSelect(member, $event)"
                  >
                    <option v-if="member.role === 'owner'" value="owner" disabled>Owner</option>
                    <option value="admin">Admin</option>
                    <option value="member">Member</option>
                  </select>
                  <button
                    type="button"
                    class="danger-action"
                    :disabled="!canRemoveMember(member) || removingUserId === member.userId"
                    @click="handleRemoveMember(member)"
                  >
                    {{ removingUserId === member.userId ? '移除中' : '移除' }}
                  </button>
                </div>
              </div>
            </div>
          </section>
        </template>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import {
  createOrganization,
  getOrganizationMembers,
  getOrganizations,
  joinOrganization,
  refreshOrganizationInviteCode,
  removeOrganizationMember,
  updateOrganizationMemberRole,
  type OrganizationMember,
  type OrganizationRole,
  type OrganizationSummary,
} from '../api'

const WORKSPACE_UPDATED_EVENT = 'organization-workspace-updated'

const organizations = ref<OrganizationSummary[]>([])
const members = ref<OrganizationMember[]>([])
const inviteCodes = ref<Record<number, string>>({})
const selectedOrganizationId = ref<number | null>(null)
const createName = ref('')
const joinCode = ref('')
const isOrganizationsLoading = ref(false)
const isMembersLoading = ref(false)
const isCreateLoading = ref(false)
const isJoinLoading = ref(false)
const isInviteLoading = ref(false)
const removingUserId = ref<number | null>(null)

const selectedOrganization = computed(() =>
  organizations.value.find((organization) => organization.organizationId === selectedOrganizationId.value) ?? null,
)

const visibleInviteCode = computed(() => {
  if (!selectedOrganization.value) {
    return ''
  }

  return inviteCodes.value[selectedOrganization.value.organizationId] ?? ''
})

const canRefreshInvite = computed(() => {
  const role = selectedOrganization.value?.role
  return role === 'owner' || role === 'admin'
})

async function loadOrganizations() {
  isOrganizationsLoading.value = true

  try {
    const previousSelection = selectedOrganizationId.value
    organizations.value = await getOrganizations({ showErrorMessage: false })

    if (organizations.value.some((organization) => organization.organizationId === previousSelection)) {
      selectedOrganizationId.value = previousSelection
    } else {
      selectedOrganizationId.value = organizations.value[0]?.organizationId ?? null
    }
  } catch (error) {
    organizations.value = []
    selectedOrganizationId.value = null
    ElMessage.error(error instanceof Error ? error.message : '组织加载失败')
  } finally {
    isOrganizationsLoading.value = false
  }
}

async function loadMembers() {
  if (!selectedOrganization.value) {
    members.value = []
    return
  }

  isMembersLoading.value = true

  try {
    members.value = await getOrganizationMembers(selectedOrganization.value.organizationId, {
      showErrorMessage: false,
    })
  } catch (error) {
    members.value = []
    ElMessage.error(error instanceof Error ? error.message : '成员加载失败')
  } finally {
    isMembersLoading.value = false
  }
}

async function handleCreateOrganization() {
  if (!createName.value) {
    return
  }

  isCreateLoading.value = true

  try {
    const created = await createOrganization(createName.value)
    inviteCodes.value = {
      ...inviteCodes.value,
      [created.organizationId]: created.inviteCode,
    }
    createName.value = ''
    selectedOrganizationId.value = created.organizationId
    await loadOrganizations()
    dispatchWorkspaceUpdated()
    ElMessage.success('组织已创建')
  } finally {
    isCreateLoading.value = false
  }
}

async function handleJoinOrganization() {
  if (!joinCode.value) {
    return
  }

  isJoinLoading.value = true

  try {
    const joined = await joinOrganization(joinCode.value)
    joinCode.value = ''
    selectedOrganizationId.value = joined.organizationId
    await loadOrganizations()
    dispatchWorkspaceUpdated()
    ElMessage.success('已加入组织')
  } finally {
    isJoinLoading.value = false
  }
}

async function handleRefreshInviteCode() {
  if (!selectedOrganization.value) {
    return
  }

  isInviteLoading.value = true

  try {
    const organizationId = selectedOrganization.value.organizationId
    const data = await refreshOrganizationInviteCode(organizationId)
    inviteCodes.value = {
      ...inviteCodes.value,
      [organizationId]: data.inviteCode,
    }
    ElMessage.success('邀请码已刷新')
  } finally {
    isInviteLoading.value = false
  }
}

async function handleRoleSelect(member: OrganizationMember, event: Event) {
  const target = event.target as HTMLSelectElement
  const role = target.value as Exclude<OrganizationRole, 'owner'>

  if (!selectedOrganization.value || role === member.role) {
    return
  }

  try {
    await updateOrganizationMemberRole(selectedOrganization.value.organizationId, member.userId, role)
    await loadMembers()
    ElMessage.success('成员角色已更新')
  } catch {
    target.value = member.role
  }
}

async function handleRemoveMember(member: OrganizationMember) {
  if (!selectedOrganization.value) {
    return
  }

  try {
    await ElMessageBox.confirm(`移除 ${member.displayName}？`, '成员移除', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  removingUserId.value = member.userId

  try {
    await removeOrganizationMember(selectedOrganization.value.organizationId, member.userId)
    await loadMembers()
    dispatchWorkspaceUpdated()
    ElMessage.success('成员已移除')
  } finally {
    removingUserId.value = null
  }
}

function canEditRole(member: OrganizationMember) {
  return selectedOrganization.value?.role === 'owner' && member.role !== 'owner'
}

function canRemoveMember(member: OrganizationMember) {
  const role = selectedOrganization.value?.role
  return (role === 'owner' || role === 'admin') && member.role === 'member'
}

function roleLabel(role: OrganizationRole) {
  const labels: Record<OrganizationRole, string> = {
    owner: 'Owner',
    admin: 'Admin',
    member: 'Member',
  }

  return labels[role]
}

function dispatchWorkspaceUpdated() {
  window.dispatchEvent(new CustomEvent(WORKSPACE_UPDATED_EVENT))
}

watch(
  () => selectedOrganization.value?.organizationId,
  () => {
    void loadMembers()
  },
)

onMounted(() => {
  void loadOrganizations()
})
</script>

<style scoped>
.settings-page {
  color: #101114;
}

.organization-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px;
  align-items: stretch;
  overflow: hidden;
  border: 1px solid rgba(16, 17, 20, 0.1);
  border-radius: 8px;
  padding: 26px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.88), rgba(246, 248, 252, 0.74)),
    repeating-linear-gradient(90deg, rgba(16, 17, 20, 0.04) 0 1px, transparent 1px 28px);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.hero-copy {
  display: grid;
  gap: 10px;
}

.hero-copy h1 {
  max-width: none;
  font-size: 34px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(92px, 1fr));
  gap: 10px;
}

.hero-metrics div {
  display: grid;
  align-content: center;
  gap: 6px;
  min-width: 0;
  border: 1px solid rgba(16, 17, 20, 0.1);
  border-radius: 6px;
  padding: 14px;
  background: #ffffff;
}

.hero-metrics span {
  overflow: hidden;
  color: #101114;
  font-size: 22px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-metrics strong {
  color: #6a717c;
  font-size: 12px;
}

.organization-console {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 18px;
  min-height: 620px;
}

.org-rail,
.org-workbench {
  min-width: 0;
  border: 1px solid rgba(16, 17, 20, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(24px);
}

.org-rail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.rail-header,
.workbench-header,
.member-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.rail-header h2,
.workbench-header h2,
.member-panel-head h2,
.workbench-empty h2 {
  margin-top: 4px;
  font-size: 20px;
}

.icon-tool {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(16, 17, 20, 0.12);
  border-radius: 6px;
  color: #101114;
  background: #ffffff;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    transform 160ms ease;
}

.icon-tool:hover {
  border-color: rgba(21, 99, 255, 0.42);
  transform: translateY(-1px);
}

.icon-tool:disabled {
  cursor: wait;
  opacity: 0.55;
  transform: none;
}

.icon-tool svg {
  width: 17px;
  height: 17px;
}

.org-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 7px;
  padding: 10px;
  color: #101114;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition:
    background 160ms ease,
    border-color 160ms ease,
    transform 160ms ease;
}

.org-item:hover,
.org-item.active {
  border-color: rgba(16, 17, 20, 0.12);
  background: #f5f7fb;
}

.org-item:hover {
  transform: translateX(2px);
}

.org-mark,
.member-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: #101114;
  font-weight: 950;
}

.org-mark {
  width: 36px;
  height: 36px;
  border-radius: 6px;
}

.org-item strong,
.org-item small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-item strong {
  font-size: 14px;
}

.org-item small,
.muted,
.member-cell small {
  color: #737a86;
  font-size: 12px;
}

.rail-form {
  display: grid;
  gap: 10px;
  margin-top: 8px;
  border-top: 1px solid rgba(16, 17, 20, 0.08);
  padding-top: 14px;
}

.join-form {
  margin-top: 0;
}

.rail-form label {
  display: grid;
  gap: 7px;
}

.rail-form label span {
  color: #101114;
  font-size: 12px;
  font-weight: 900;
}

.rail-form input,
.member-actions select {
  width: 100%;
  min-height: 40px;
  border: 1px solid rgba(16, 17, 20, 0.12);
  border-radius: 6px;
  outline: 0;
  color: #101114;
  background: #ffffff;
}

.rail-form input {
  padding: 0 11px;
}

.rail-form input:focus,
.member-actions select:focus {
  border-color: rgba(21, 99, 255, 0.52);
  box-shadow: 0 0 0 4px rgba(21, 99, 255, 0.09);
}

.solid-action,
.line-action,
.danger-action {
  min-height: 40px;
  border-radius: 6px;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 950;
  transition:
    opacity 160ms ease,
    transform 160ms ease;
}

.solid-action {
  color: #ffffff;
  background: #101114;
}

.line-action {
  color: #101114;
  border: 1px solid rgba(16, 17, 20, 0.14);
  background: #ffffff;
}

.danger-action {
  color: #b42318;
  border: 1px solid #ffd1cc;
  background: #fff4f2;
}

.solid-action:hover,
.line-action:hover,
.danger-action:hover {
  transform: translateY(-1px);
}

.solid-action:disabled,
.line-action:disabled,
.danger-action:disabled {
  cursor: not-allowed;
  opacity: 0.48;
  transform: none;
}

.compact {
  min-width: 116px;
}

.org-workbench {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 14px;
  padding: 16px;
}

.workbench-empty {
  display: grid;
  align-content: center;
  justify-items: center;
  min-height: 420px;
  color: #737a86;
  text-align: center;
}

.invite-strip,
.member-panel {
  border: 1px solid rgba(16, 17, 20, 0.1);
  border-radius: 8px;
  background: #ffffff;
}

.invite-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
}

.invite-strip strong {
  display: block;
  margin-top: 6px;
  color: #101114;
  font-size: 24px;
  letter-spacing: 0;
}

.member-panel {
  display: grid;
  gap: 14px;
  min-height: 0;
  padding: 16px;
}

.member-table {
  display: grid;
  gap: 8px;
}

.member-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.3fr) minmax(90px, 0.7fr) minmax(86px, 0.4fr) minmax(210px, 0.9fr);
  gap: 12px;
  align-items: center;
  min-height: 58px;
  border: 1px solid rgba(16, 17, 20, 0.08);
  border-radius: 7px;
  padding: 10px;
  background: #fbfcfe;
}

.table-head {
  min-height: 38px;
  color: #737a86;
  background: transparent;
  font-size: 12px;
  font-weight: 950;
  text-transform: uppercase;
}

.member-cell {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.member-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  font-size: 13px;
}

.member-cell strong {
  display: block;
  overflow: hidden;
  color: #101114;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-width: 72px;
  min-height: 28px;
  border-radius: 999px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 950;
}

.role-pill.owner {
  color: #7a2e00;
  background: #ffe2c4;
}

.role-pill.admin {
  color: #083f91;
  background: #dce9ff;
}

.role-pill.member {
  color: #075143;
  background: #cdf7e9;
}

.member-actions {
  display: grid;
  grid-template-columns: minmax(88px, 1fr) auto;
  gap: 8px;
}

.member-actions select {
  padding: 0 8px;
}

.empty-state {
  border: 1px dashed rgba(16, 17, 20, 0.16);
  border-radius: 7px;
  padding: 18px;
  color: #737a86;
  background: #fbfcfe;
  font-size: 13px;
  font-weight: 800;
  text-align: center;
}

@media (max-width: 1180px) {
  .organization-hero,
  .organization-console {
    grid-template-columns: 1fr;
  }

  .hero-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .organization-hero {
    padding: 18px;
  }

  .hero-metrics,
  .member-row {
    grid-template-columns: 1fr;
  }

  .member-actions {
    grid-template-columns: 1fr;
  }

  .invite-strip {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
