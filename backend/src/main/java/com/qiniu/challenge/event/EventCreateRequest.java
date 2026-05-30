package com.qiniu.challenge.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record EventCreateRequest(
        @NotNull
        Long calendarSpaceId,

        @NotBlank
        @Size(max = 200)
        String title,

        OffsetDateTime startTime,

        OffsetDateTime endTime,

        @Size(max = 200)
        String location,

        String description,

        @Size(max = 64)
        String timezone,

        Boolean allDay,

        @Size(max = 32)
        String visibility,

        @Size(max = 32)
        String source,

        @Size(max = 32)
        String repeatType,

        OffsetDateTime repeatUntil,

        Integer repeatCount,

        String repeatRuleText,

        String notes,

        List<Long> participantUserIds,

        @Valid
        EventEnterpriseFields enterpriseFields) {
}
