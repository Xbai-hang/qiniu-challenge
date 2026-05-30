package com.qiniu.challenge.reminder;

import java.time.OffsetDateTime;

public record NotificationRecord(
        long id,
        long userId,
        long calendarSpaceId,
        Long reminderId,
        String type,
        String title,
        String content,
        String payload,
        String status,
        OffsetDateTime pushedAt,
        OffsetDateTime readAt,
        OffsetDateTime createdAt) {
}
