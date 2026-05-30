package com.qiniu.challenge.space;

public record CalendarSpaceResponse(
        Long id,
        String type,
        String name,
        Long organizationId,
        String role
) {
}
