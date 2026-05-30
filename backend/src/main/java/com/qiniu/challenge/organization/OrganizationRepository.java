package com.qiniu.challenge.organization;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {

    long createOrganization(String name, String inviteCode, long createdBy);

    long createOrganizationSpace(long organizationId, String name);

    void addMember(long organizationId, long userId, OrganizationRole role);

    boolean reactivateMember(long organizationId, long userId, OrganizationRole role);

    boolean hasAnyMemberRecord(long organizationId, long userId);

    Optional<OrganizationRecord> findActiveOrganization(long organizationId);

    Optional<OrganizationRecord> findActiveOrganizationByInviteCode(String inviteCode);

    Optional<OrganizationRole> findActiveMemberRole(long organizationId, long userId);

    Optional<Long> findOrganizationSpaceId(long organizationId);

    List<OrganizationSummaryResponse> findOrganizationsForUser(long userId);

    List<OrganizationMemberResponse> findActiveMembers(long organizationId);

    boolean updateInviteCode(long organizationId, String inviteCode);

    boolean updateMemberRole(long organizationId, long userId, OrganizationRole role);

    boolean removeMember(long organizationId, long userId);
}
