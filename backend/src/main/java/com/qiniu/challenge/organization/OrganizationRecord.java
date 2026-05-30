package com.qiniu.challenge.organization;

record OrganizationRecord(
        long id,
        String name,
        String inviteCode,
        long createdBy,
        String status
) {
}
