package com.qiniu.challenge.reminder;

import java.time.OffsetDateTime;

public record CreateReminderCommand(
        long eventId,
        long calendarSpaceId,
        long userId,
        Integer offsetMinutes,
        OffsetDateTime triggerAt,
        Long snoozedFromId,
        long createdBy) {
}
