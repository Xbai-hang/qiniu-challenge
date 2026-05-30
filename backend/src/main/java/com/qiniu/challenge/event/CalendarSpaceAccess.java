package com.qiniu.challenge.event;

public record CalendarSpaceAccess(
        long id,
        String type,
        String name,
        Long ownerUserId,
        Long organizationId,
        String role) {

    public boolean personal() {
        return "personal".equals(type);
    }

    public boolean organization() {
        return "organization".equals(type);
    }
}
