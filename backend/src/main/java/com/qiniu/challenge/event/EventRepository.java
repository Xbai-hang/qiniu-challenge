package com.qiniu.challenge.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository {

    Optional<CalendarSpaceAccess> findAccessibleSpace(long spaceId, long userId);

    boolean areActiveOrganizationMembers(long organizationId, List<Long> userIds);

    long createEvent(CalendarEvent event);

    Optional<CalendarEvent> findEvent(long eventId);

    List<CalendarEvent> findEvents(EventSearchRequest request, long currentUserId);

    List<EventConflict> findConflicts(
            long currentUserId,
            List<Long> participantUserIds,
            OffsetDateTime start,
            OffsetDateTime end,
            Long excludeEventId);

    boolean updateEvent(CalendarEvent event, int expectedVersion);

    boolean softDeleteEvent(long eventId);

    List<EventParticipant> findParticipants(long eventId);

    void replaceParticipants(long eventId, List<ParticipantCommand> participants);

    boolean isParticipant(long eventId, long userId);

    OffsetDateTime now();
}
