package com.qiniu.challenge.reminder;

import java.time.OffsetDateTime;

public record SnoozeReminderResponse(
        long oldReminderId,
        long newReminderId,
        OffsetDateTime triggerAt,
        String status) {
}
