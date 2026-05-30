package com.qiniu.challenge.event;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final String DEFAULT_VISIBILITY = "space";
    private static final String DEFAULT_SOURCE = "manual";
    private static final String DEFAULT_REPEAT_TYPE = "none";
    private static final String ORGANIZER = "organizer";
    private static final String ATTENDEE = "attendee";
    private static final String NEEDS_ACTION = "needs_action";
    private static final String ACCEPTED = "accepted";

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse createEvent(long currentUserId, EventCreateRequest request) {
        CalendarSpaceAccess space = requireAccessibleSpace(request.calendarSpaceId(), currentUserId);
        OffsetDateTime startTime = request.startTime() == null ? eventRepository.now() : request.startTime();
        OffsetDateTime endTime = request.endTime() == null ? startTime.plusMinutes(60) : request.endTime();
        validateTimeRange(startTime, endTime);

        EventEnterpriseFields fields = request.enterpriseFields();
        Long ownerUserId = fields == null ? null : fields.ownerUserId();
        validateEnterpriseUsers(space, currentUserId, request.participantUserIds(), ownerUserId);

        CalendarEvent event = new CalendarEvent(
                0,
                space.id(),
                space.organizationId(),
                currentUserId,
                request.title().trim(),
                blankToNull(request.description()),
                blankToNull(request.location()),
                startTime,
                endTime,
                defaultString(request.timezone(), DEFAULT_TIMEZONE),
                request.allDay() != null && request.allDay(),
                defaultString(request.visibility(), DEFAULT_VISIBILITY),
                defaultString(request.source(), DEFAULT_SOURCE),
                defaultString(request.repeatType(), DEFAULT_REPEAT_TYPE),
                request.repeatUntil(),
                request.repeatCount(),
                blankToNull(request.repeatRuleText()),
                fields == null ? null : blankToNull(fields.project()),
                ownerUserId,
                fields == null ? null : blankToNull(fields.status()),
                fields == null ? null : blankToNull(fields.priority()),
                normalizeTags(fields == null ? null : fields.tags()),
                fields == null ? null : blankToNull(fields.eventType()),
                blankToNull(request.notes()),
                fields == null ? null : blankToNull(fields.customFields()),
                0);

        long eventId = eventRepository.createEvent(event);
        eventRepository.replaceParticipants(eventId, buildParticipants(currentUserId, request.participantUserIds()));
        return getEvent(currentUserId, eventId);
    }

    public EventResponse getEvent(long currentUserId, long eventId) {
        CalendarEvent event = requireVisibleEvent(currentUserId, eventId);
        return EventResponse.of(event, eventRepository.findParticipants(event.id()));
    }

    public List<EventResponse> listEvents(
            long currentUserId,
            Long calendarSpaceId,
            OffsetDateTime start,
            OffsetDateTime end,
            String keyword,
            String project,
            Long ownerUserId,
            String status,
            String priority,
            String tag,
            String sortBy,
            String sortDirection) {
        if (calendarSpaceId != null) {
            requireAccessibleSpace(calendarSpaceId, currentUserId);
        }
        if (start != null && end != null) {
            validateTimeRange(start, end);
        }
        EventSearchRequest request = new EventSearchRequest(
                calendarSpaceId,
                start,
                end,
                keyword,
                project,
                ownerUserId,
                status,
                priority,
                tag,
                sortBy,
                sortDirection);
        return eventRepository.findEvents(request, currentUserId).stream()
                .map(event -> EventResponse.of(event, eventRepository.findParticipants(event.id())))
                .toList();
    }

    public List<EventResponse> searchEvents(long currentUserId, Long calendarSpaceId, String keyword, Integer limit) {
        List<EventResponse> events = listEvents(
                currentUserId,
                calendarSpaceId,
                null,
                null,
                keyword,
                null,
                null,
                null,
                null,
                null,
                "startTime",
                "asc");
        int max = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return events.stream().limit(max).toList();
    }

    @Transactional
    public EventResponse updateEvent(long currentUserId, long eventId, EventUpdateRequest request) {
        CalendarEvent existing = requireVisibleEvent(currentUserId, eventId);
        CalendarSpaceAccess space = requireAccessibleSpace(existing.calendarSpaceId(), currentUserId);
        int expectedVersion = request.version() == null ? existing.version() : request.version();

        OffsetDateTime startTime = request.startTime() == null ? existing.startTime() : request.startTime();
        OffsetDateTime endTime = request.endTime() == null ? existing.endTime() : request.endTime();
        validateTimeRange(startTime, endTime);

        EventEnterpriseFields fields = request.enterpriseFields();
        Long ownerUserId = fields == null || fields.ownerUserId() == null
                ? existing.ownerUserId()
                : fields.ownerUserId();
        List<Long> requestedParticipants = request.participantUserIds();
        validateEnterpriseUsers(space, currentUserId, requestedParticipants, ownerUserId);

        CalendarEvent updated = new CalendarEvent(
                existing.id(),
                existing.calendarSpaceId(),
                existing.organizationId(),
                existing.createdBy(),
                request.title() == null ? existing.title() : request.title().trim(),
                request.description() == null ? existing.description() : blankToNull(request.description()),
                request.location() == null ? existing.location() : blankToNull(request.location()),
                startTime,
                endTime,
                request.timezone() == null ? existing.timezone() : defaultString(request.timezone(), DEFAULT_TIMEZONE),
                request.allDay() == null ? existing.allDay() : request.allDay(),
                request.visibility() == null ? existing.visibility() : defaultString(request.visibility(), DEFAULT_VISIBILITY),
                existing.source(),
                request.repeatType() == null ? existing.repeatType() : defaultString(request.repeatType(), DEFAULT_REPEAT_TYPE),
                request.repeatUntil() == null ? existing.repeatUntil() : request.repeatUntil(),
                request.repeatCount() == null ? existing.repeatCount() : request.repeatCount(),
                request.repeatRuleText() == null ? existing.repeatRuleText() : blankToNull(request.repeatRuleText()),
                mergeString(existing.project(), fields, fields == null ? null : fields.project()),
                ownerUserId,
                mergeString(existing.status(), fields, fields == null ? null : fields.status()),
                mergeString(existing.priority(), fields, fields == null ? null : fields.priority()),
                fields == null || fields.tags() == null ? existing.tags() : normalizeTags(fields.tags()),
                mergeString(existing.eventType(), fields, fields == null ? null : fields.eventType()),
                request.notes() == null ? existing.notes() : blankToNull(request.notes()),
                mergeString(existing.customFields(), fields, fields == null ? null : fields.customFields()),
                existing.version());

        if (!eventRepository.updateEvent(updated, expectedVersion)) {
            throw new ApiException(ErrorCode.CONFLICT, "该事件已被其他操作修改");
        }

        if (requestedParticipants != null) {
            eventRepository.replaceParticipants(eventId, buildParticipants(existing.createdBy(), requestedParticipants));
        }
        return getEvent(currentUserId, eventId);
    }

    @Transactional
    public boolean deleteEvent(long currentUserId, long eventId) {
        CalendarEvent event = requireVisibleEvent(currentUserId, eventId);
        requireAccessibleSpace(event.calendarSpaceId(), currentUserId);
        if (!eventRepository.softDeleteEvent(eventId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "事件不存在");
        }
        return true;
    }

    private CalendarEvent requireVisibleEvent(long currentUserId, long eventId) {
        CalendarEvent event = eventRepository.findEvent(eventId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "事件不存在"));
        requireAccessibleSpace(event.calendarSpaceId(), currentUserId);
        if (!"space".equals(event.visibility())
                && event.createdBy() != currentUserId
                && !eventRepository.isParticipant(event.id(), currentUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return event;
    }

    private CalendarSpaceAccess requireAccessibleSpace(long spaceId, long currentUserId) {
        return eventRepository.findAccessibleSpace(spaceId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "无权访问该日历空间"));
    }

    private void validateEnterpriseUsers(
            CalendarSpaceAccess space,
            long currentUserId,
            List<Long> participantUserIds,
            Long ownerUserId) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (participantUserIds != null) {
            userIds.addAll(participantUserIds);
        }
        if (ownerUserId != null) {
            userIds.add(ownerUserId);
        }
        if (userIds.isEmpty()) {
            return;
        }
        if (space.personal()) {
            boolean onlyCurrentUser = userIds.stream().allMatch(userId -> userId == currentUserId);
            if (!onlyCurrentUser) {
                throw new ApiException(ErrorCode.FORBIDDEN, "个人事件只能包含创建者");
            }
            return;
        }
        if (space.organizationId() == null || !eventRepository.areActiveOrganizationMembers(
                space.organizationId(),
                new ArrayList<>(userIds))) {
            throw new ApiException(ErrorCode.FORBIDDEN, "参与人必须是组织成员");
        }
    }

    private List<ParticipantCommand> buildParticipants(long organizerUserId, List<Long> attendeeUserIds) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        userIds.add(organizerUserId);
        if (attendeeUserIds != null) {
            userIds.addAll(attendeeUserIds);
        }
        return userIds.stream()
                .map(userId -> new ParticipantCommand(
                        userId,
                        userId == organizerUserId ? ORGANIZER : ATTENDEE,
                        userId == organizerUserId ? ACCEPTED : NEEDS_ACTION))
                .toList();
    }

    private void validateTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String defaultString(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String mergeString(String existing, EventEnterpriseFields fields, String requested) {
        if (fields == null || requested == null) {
            return existing;
        }
        return blankToNull(requested);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
