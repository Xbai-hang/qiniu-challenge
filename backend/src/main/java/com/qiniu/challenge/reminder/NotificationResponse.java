package com.qiniu.challenge.reminder;

import java.time.OffsetDateTime;

public record NotificationResponse(
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

    public static NotificationResponse of(NotificationRecord notification) {
        return new NotificationResponse(
                notification.id(),
                notification.userId(),
                notification.calendarSpaceId(),
                notification.reminderId(),
                notification.type(),
                notification.title(),
                notification.content(),
                notification.payload(),
                notification.status(),
                notification.pushedAt(),
                notification.readAt(),
                notification.createdAt());
    }
}
