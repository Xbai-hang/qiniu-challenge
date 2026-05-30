package com.qiniu.challenge.reminder;

import java.time.OffsetDateTime;

public record EventReminder(
        long id,
        long eventId,
        long calendarSpaceId,
        long userId,
        Integer offsetMinutes,
        OffsetDateTime triggerAt,
        String status,
        Long snoozedFromId,
        long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime cancelledAt) {
}
