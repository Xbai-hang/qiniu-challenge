package com.qiniu.challenge.organization;

public record JoinOrganizationResponse(
        long organizationId,
        long spaceId,
        String role
) {
}
