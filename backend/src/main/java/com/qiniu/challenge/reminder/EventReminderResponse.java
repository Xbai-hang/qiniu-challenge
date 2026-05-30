package com.qiniu.challenge.reminder;

import java.time.OffsetDateTime;

public record EventReminderResponse(
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

    public static EventReminderResponse of(EventReminder reminder) {
        return new EventReminderResponse(
                reminder.id(),
                reminder.eventId(),
                reminder.calendarSpaceId(),
                reminder.userId(),
                reminder.offsetMinutes(),
                reminder.triggerAt(),
                reminder.status(),
                reminder.snoozedFromId(),
                reminder.createdBy(),
                reminder.createdAt(),
                reminder.updatedAt(),
                reminder.cancelledAt());
    }
}
