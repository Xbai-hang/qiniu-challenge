package com.qiniu.challenge.event;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record ConflictCheckRequest(
        @NotNull
        Long calendarSpaceId,

        Long eventId,

        List<Long> participantUserIds,

        @NotNull
        OffsetDateTime startTime,

        @NotNull
        OffsetDateTime endTime) {
}
