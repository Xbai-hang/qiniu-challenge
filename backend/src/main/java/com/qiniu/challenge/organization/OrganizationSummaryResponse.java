package com.qiniu.challenge.organization;

public record OrganizationSummaryResponse(
        long organizationId,
        String name,
        String role,
        long spaceId
) {
}
