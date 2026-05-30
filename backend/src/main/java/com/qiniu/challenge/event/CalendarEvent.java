package com.qiniu.challenge.event;

import java.time.OffsetDateTime;
import java.util.List;

public record CalendarEvent(
        long id,
        long calendarSpaceId,
        Long organizationId,
        long createdBy,
        String title,
        String description,
        String location,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String timezone,
        boolean allDay,
        String visibility,
        String source,
        String repeatType,
        OffsetDateTime repeatUntil,
        Integer repeatCount,
        String repeatRuleText,
        String project,
        Long ownerUserId,
        String status,
        String priority,
        List<String> tags,
        String eventType,
        String notes,
        String customFields,
        int version) {
}
