package com.qiniu.challenge.organization;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private static final char[] INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int INVITE_CODE_LENGTH = 10;
    private static final int INVITE_CODE_RETRIES = 8;

    private final OrganizationRepository organizationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public CreateOrganizationResponse createOrganization(long currentUserId, CreateOrganizationRequest request) {
        String name = request.name().trim();

        for (int attempt = 0; attempt < INVITE_CODE_RETRIES; attempt++) {
            String inviteCode = generateInviteCode();
            try {
                long organizationId = organizationRepository.createOrganization(name, inviteCode, currentUserId);
                organizationRepository.addMember(organizationId, currentUserId, OrganizationRole.OWNER);
                long spaceId = organizationRepository.createOrganizationSpace(organizationId, name);
                return new CreateOrganizationResponse(
                        organizationId,
                        spaceId,
                        name,
                        OrganizationRole.OWNER.value(),
                        inviteCode);
            } catch (DuplicateKeyException exception) {
                if (attempt == INVITE_CODE_RETRIES - 1) {
                    throw new ApiException(ErrorCode.CONFLICT, "邀请码生成冲突，请重试");
                }
            }
        }

        throw new ApiException(ErrorCode.CONFLICT, "邀请码生成冲突，请重试");
    }

    public List<OrganizationSummaryResponse> findMyOrganizations(long currentUserId) {
        return organizationRepository.findOrganizationsForUser(currentUserId);
    }

    @Transactional
    public InviteCodeResponse refreshInviteCode(long currentUserId, long organizationId) {
        ensureOrganizationExists(organizationId);
        OrganizationRole role = requireActiveMemberRole(organizationId, currentUserId);
        if (!role.canRefreshInviteCode()) {
            throw forbidden();
        }

        for (int attempt = 0; attempt < INVITE_CODE_RETRIES; attempt++) {
            String inviteCode = generateInviteCode();
            try {
                if (organizationRepository.updateInviteCode(organizationId, inviteCode)) {
                    return new InviteCodeResponse(inviteCode);
                }
            } catch (DuplicateKeyException exception) {
                if (attempt == INVITE_CODE_RETRIES - 1) {
                    throw new ApiException(ErrorCode.CONFLICT, "邀请码生成冲突，请重试");
                }
            }
        }

        throw new ApiException(ErrorCode.CONFLICT, "邀请码生成冲突，请重试");
    }

    @Transactional
    public JoinOrganizationResponse joinOrganization(long currentUserId, JoinOrganizationRequest request) {
        String inviteCode = request.inviteCode().trim();
        OrganizationRecord organization = organizationRepository.findActiveOrganizationByInviteCode(inviteCode)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "邀请码无效"));

        if (organizationRepository.findActiveMemberRole(organization.id(), currentUserId).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "已加入该组织");
        }

        if (organizationRepository.hasAnyMemberRecord(organization.id(), currentUserId)) {
            organizationRepository.reactivateMember(organization.id(), currentUserId, OrganizationRole.MEMBER);
        } else {
            organizationRepository.addMember(organization.id(), currentUserId, OrganizationRole.MEMBER);
        }

        long spaceId = requireOrganizationSpaceId(organization.id());
        return new JoinOrganizationResponse(organization.id(), spaceId, OrganizationRole.MEMBER.value());
    }

    public List<OrganizationMemberResponse> findMembers(long currentUserId, long organizationId) {
        ensureOrganizationExists(organizationId);
        requireActiveMemberRole(organizationId, currentUserId);
        return organizationRepository.findActiveMembers(organizationId);
    }

    @Transactional
    public boolean updateMemberRole(
            long currentUserId,
            long organizationId,
            long targetUserId,
            UpdateMemberRoleRequest request) {
        ensureOrganizationExists(organizationId);
        OrganizationRole operatorRole = requireActiveMemberRole(organizationId, currentUserId);
        if (operatorRole != OrganizationRole.OWNER) {
            throw forbidden();
        }

        OrganizationRole newRole = OrganizationRole.fromValue(request.role());
        if (newRole == OrganizationRole.OWNER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "不能通过该接口设置 Owner 角色");
        }

        OrganizationRole targetRole = requireActiveMemberRole(organizationId, targetUserId);
        if (targetRole == OrganizationRole.OWNER) {
            throw new ApiException(ErrorCode.FORBIDDEN, "不能调整 Owner 角色");
        }

        if (!organizationRepository.updateMemberRole(organizationId, targetUserId, newRole)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "成员不存在");
        }
        return true;
    }

    @Transactional
    public boolean removeMember(long currentUserId, long organizationId, long targetUserId) {
        ensureOrganizationExists(organizationId);
        OrganizationRole operatorRole = requireActiveMemberRole(organizationId, currentUserId);
        if (!operatorRole.canManageMembers()) {
            throw forbidden();
        }

        OrganizationRole targetRole = requireActiveMemberRole(organizationId, targetUserId);
        if (targetRole != OrganizationRole.MEMBER) {
            throw new ApiException(ErrorCode.FORBIDDEN, "只能移除普通成员");
        }

        if (!organizationRepository.removeMember(organizationId, targetUserId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "成员不存在");
        }
        return true;
    }

    private void ensureOrganizationExists(long organizationId) {
        organizationRepository.findActiveOrganization(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "组织不存在"));
    }

    private OrganizationRole requireActiveMemberRole(long organizationId, long userId) {
        return organizationRepository.findActiveMemberRole(organizationId, userId)
                .orElseThrow(this::forbidden);
    }

    private long requireOrganizationSpaceId(long organizationId) {
        return organizationRepository.findOrganizationSpaceId(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "组织空间不存在"));
    }

    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            builder.append(INVITE_CODE_CHARS[secureRandom.nextInt(INVITE_CODE_CHARS.length)]);
        }
        return builder.toString();
    }

    private ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN);
    }
}
