package com.qiniu.challenge.reminder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;

public record CreateReminderRequest(
        @Min(0)
        @Max(10080)
        Integer offsetMinutes,

        OffsetDateTime triggerAt,

        Long userId) {
}
