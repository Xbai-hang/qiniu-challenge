package com.qiniu.challenge.organization;

public record OrganizationMemberResponse(
        long userId,
        String displayName,
        String nickname,
        String title,
        String role,
        String status
) {
}
