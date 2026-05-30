import { request } from './http'

export type OrganizationRole = 'owner' | 'admin' | 'member'

export type OrganizationSummary = {
  organizationId: number
  name: string
  role: OrganizationRole
  spaceId: number
}

export type CreateOrganizationResponse = {
  organizationId: number
  spaceId: number
  name: string
  role: OrganizationRole
  inviteCode: string
}

export type InviteCodeResponse = {
  inviteCode: string
}

export type JoinOrganizationResponse = {
  organizationId: number
  spaceId: number
  role: OrganizationRole
}

export type OrganizationMember = {
  userId: number
  displayName: string
  nickname: string
  title?: string
  role: OrganizationRole
  status: 'active' | 'removed' | string
}

export function createOrganization(name: string) {
  return request<CreateOrganizationResponse>('/organizations', {
    method: 'POST',
    body: { name },
  })
}

export function getOrganizations(options: { showErrorMessage?: boolean } = {}) {
  return request<OrganizationSummary[]>('/organizations', {
    showErrorMessage: options.showErrorMessage,
  })
}

export function refreshOrganizationInviteCode(organizationId: number) {
  return request<InviteCodeResponse>(`/organizations/${organizationId}/invite-code/refresh`, {
    method: 'POST',
  })
}

export function joinOrganization(inviteCode: string) {
  return request<JoinOrganizationResponse>('/organizations/join', {
    method: 'POST',
    body: { inviteCode },
  })
}

export function getOrganizationMembers(
  organizationId: number,
  options: { showErrorMessage?: boolean } = {},
) {
  return request<OrganizationMember[]>(`/organizations/${organizationId}/members`, {
    showErrorMessage: options.showErrorMessage,
  })
}

export function updateOrganizationMemberRole(
  organizationId: number,
  userId: number,
  role: Exclude<OrganizationRole, 'owner'>,
) {
  return request<boolean>(`/organizations/${organizationId}/members/${userId}/role`, {
    method: 'PATCH',
    body: { role },
  })
}

export function removeOrganizationMember(organizationId: number, userId: number) {
  return request<boolean>(`/organizations/${organizationId}/members/${userId}`, {
    method: 'DELETE',
  })
}
