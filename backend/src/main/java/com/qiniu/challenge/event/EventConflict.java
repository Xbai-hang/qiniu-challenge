package com.qiniu.challenge.event;

import java.time.OffsetDateTime;

public record EventConflict(
        long eventId,
        long calendarSpaceId,
        String calendarSpaceName,
        String title,
        long participantUserId,
        String participantName,
        OffsetDateTime startTime,
        OffsetDateTime endTime) {
}
