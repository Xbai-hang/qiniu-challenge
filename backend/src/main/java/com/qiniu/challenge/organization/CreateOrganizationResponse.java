package com.qiniu.challenge.organization;

public record CreateOrganizationResponse(
        long organizationId,
        long spaceId,
        String name,
        String role,
        String inviteCode
) {
}
