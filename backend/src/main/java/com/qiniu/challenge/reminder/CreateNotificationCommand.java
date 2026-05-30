package com.qiniu.challenge.reminder;

public record CreateNotificationCommand(
        long userId,
        long calendarSpaceId,
        Long reminderId,
        String type,
        String title,
        String content,
        String payload) {
}
