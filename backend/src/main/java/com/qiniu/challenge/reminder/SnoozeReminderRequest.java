package com.qiniu.challenge.reminder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SnoozeReminderRequest(
        @Min(1)
        @Max(1440)
        Integer minutes) {
}
