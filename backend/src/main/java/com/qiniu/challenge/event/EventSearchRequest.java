package com.qiniu.challenge.event;

import java.time.OffsetDateTime;

public record EventSearchRequest(
        Long calendarSpaceId,
        OffsetDateTime start,
        OffsetDateTime end,
        String keyword,
        String project,
        Long ownerUserId,
        String status,
        String priority,
        String tag,
        String sortBy,
        String sortDirection) {
}
