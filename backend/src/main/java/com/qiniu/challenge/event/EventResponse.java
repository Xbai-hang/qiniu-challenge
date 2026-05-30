package com.qiniu.challenge.event;

import java.time.OffsetDateTime;
import java.util.List;

public record EventResponse(
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
        int version,
        List<EventParticipant> participants) {

    public static EventResponse of(CalendarEvent event, List<EventParticipant> participants) {
        return new EventResponse(
                event.id(),
                event.calendarSpaceId(),
                event.organizationId(),
                event.createdBy(),
                event.title(),
                event.description(),
                event.location(),
                event.startTime(),
                event.endTime(),
                event.timezone(),
                event.allDay(),
                event.visibility(),
                event.source(),
                event.repeatType(),
                event.repeatUntil(),
                event.repeatCount(),
                event.repeatRuleText(),
                event.project(),
                event.ownerUserId(),
                event.status(),
                event.priority(),
                event.tags(),
                event.eventType(),
                event.notes(),
                event.customFields(),
                event.version(),
                participants);
    }
}
