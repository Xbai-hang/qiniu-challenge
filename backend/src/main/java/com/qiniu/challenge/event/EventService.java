package com.qiniu.challenge.event;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.reminder.CreateNotificationCommand;
import com.qiniu.challenge.reminder.NotificationRecord;
import com.qiniu.challenge.reminder.NotificationResponse;
import com.qiniu.challenge.reminder.NotificationWebSocketHandler;
import com.qiniu.challenge.reminder.ReminderRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final String MANUAL_SOURCE = "manual";
    private static final String SYSTEM_SOURCE = "system";
    private static final String EVENT_TARGET = "event";
    private static final String NOTIFICATION_TARGET = "notification";

    private final EventRepository eventRepository;
    private final PermissionService permissionService;
    private final ConflictService conflictService;
    private final OperationLogRepository operationLogRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public EventService(
            EventRepository eventRepository,
            PermissionService permissionService,
            ConflictService conflictService,
            OperationLogRepository operationLogRepository,
            ReminderRepository reminderRepository,
            NotificationWebSocketHandler notificationWebSocketHandler) {
        this.eventRepository = eventRepository;
        this.permissionService = permissionService;
        this.conflictService = conflictService;
        this.operationLogRepository = operationLogRepository;
        this.reminderRepository = reminderRepository;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Transactional
    public EventResponse createEvent(long currentUserId, EventCreateRequest request) {
        CalendarSpaceAccess space = permissionService.requireSpaceAccess(request.calendarSpaceId(), currentUserId);
        permissionService.requireCanCreateEvent(space);
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

        List<ParticipantCommand> participants = buildParticipants(currentUserId, request.participantUserIds());
        List<EventConflict> conflicts = conflictService.detectConflicts(
                currentUserId,
                participants.stream().map(ParticipantCommand::userId).toList(),
                startTime,
                endTime,
                null);
        requireConflictConfirmation(conflicts, request.forceCreateOnConflict());

        long eventId = eventRepository.createEvent(event);
        eventRepository.replaceParticipants(eventId, participants);
        EventResponse created = snapshot(eventId);
        operationLogRepository.create(new OperationLogEntry(
                currentUserId,
                created.calendarSpaceId(),
                MANUAL_SOURCE,
                "create",
                EVENT_TARGET,
                created.id(),
                null,
                created,
                false));
        notifyAddedParticipants(currentUserId, space, created, notifiedUserIds(
                participantsToUserIds(participants),
                ownerUserId,
                currentUserId));
        return snapshot(eventId, conflicts);
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
            permissionService.requireSpaceAccess(calendarSpaceId, currentUserId);
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
                .map(event -> eventWithConflicts(currentUserId, event))
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
        CalendarEvent existing = requireEventForWrite(currentUserId, eventId);
        CalendarSpaceAccess space = permissionService.requireSpaceAccess(existing.calendarSpaceId(), currentUserId);
        permissionService.requireCanUpdateEvent(space, existing, currentUserId);
        EventResponse beforeSnapshot = EventResponse.of(existing, eventRepository.findParticipants(eventId));
        int expectedVersion = request.version() == null ? existing.version() : request.version();

        OffsetDateTime startTime = request.startTime() == null ? existing.startTime() : request.startTime();
        OffsetDateTime endTime = request.endTime() == null ? existing.endTime() : request.endTime();
        validateTimeRange(startTime, endTime);

        EventEnterpriseFields fields = request.enterpriseFields();
        Long ownerUserId = fields == null || fields.ownerUserId() == null
                ? existing.ownerUserId()
                : fields.ownerUserId();
        List<Long> requestedParticipants = request.participantUserIds();
        Set<Long> existingParticipantIds = eventRepository.findParticipants(eventId).stream()
                .map(EventParticipant::userId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Long existingOwnerUserId = existing.ownerUserId();
        validateEnterpriseUsers(space, currentUserId, requestedParticipants, ownerUserId);
        List<ParticipantCommand> finalParticipants = requestedParticipants == null
                ? existingParticipantIds.stream()
                        .map(participant -> new ParticipantCommand(
                                participant,
                                participant == existing.createdBy() ? ORGANIZER : ATTENDEE,
                                participant == existing.createdBy() ? ACCEPTED : NEEDS_ACTION))
                        .toList()
                : buildParticipants(existing.createdBy(), requestedParticipants);
        List<EventConflict> conflicts = conflictService.detectConflicts(
                currentUserId,
                finalParticipants.stream().map(ParticipantCommand::userId).toList(),
                startTime,
                endTime,
                eventId);
        requireConflictConfirmation(conflicts, request.forceUpdateOnConflict());

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
            eventRepository.replaceParticipants(eventId, finalParticipants);
        }
        EventResponse afterSnapshot = snapshot(eventId);
        operationLogRepository.create(new OperationLogEntry(
                currentUserId,
                afterSnapshot.calendarSpaceId(),
                MANUAL_SOURCE,
                "update",
                EVENT_TARGET,
                afterSnapshot.id(),
                beforeSnapshot,
                afterSnapshot,
                false));
        if (requestedParticipants != null) {
            List<Long> addedParticipantIds = finalParticipants.stream()
                    .map(ParticipantCommand::userId)
                    .filter(userId -> !existingParticipantIds.contains(userId))
                    .toList();
            notifyAddedParticipants(currentUserId, space, afterSnapshot, notifiedUserIds(
                    addedParticipantIds,
                    newlyAssignedOwnerUserId(existingOwnerUserId, ownerUserId),
                    currentUserId));
        } else if (newlyAssignedOwnerUserId(existingOwnerUserId, ownerUserId) != null) {
            notifyAddedParticipants(currentUserId, space, afterSnapshot, notifiedUserIds(
                    List.of(),
                    ownerUserId,
                    currentUserId));
        }
        return snapshot(eventId, conflicts);
    }

    @Transactional
    public boolean deleteEvent(long currentUserId, long eventId) {
        CalendarEvent event = requireEventForWrite(currentUserId, eventId);
        CalendarSpaceAccess space = permissionService.requireSpaceAccess(event.calendarSpaceId(), currentUserId);
        permissionService.requireCanDeleteEvent(space, event, currentUserId);
        EventResponse beforeSnapshot = EventResponse.of(event, eventRepository.findParticipants(eventId));
        if (!eventRepository.softDeleteEvent(eventId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "事件不存在");
        }
        operationLogRepository.create(new OperationLogEntry(
                currentUserId,
                event.calendarSpaceId(),
                MANUAL_SOURCE,
                "delete",
                EVENT_TARGET,
                eventId,
                beforeSnapshot,
                null,
                false));
        return true;
    }

    public ConflictCheckResponse checkConflicts(long currentUserId, ConflictCheckRequest request) {
        CalendarSpaceAccess space = permissionService.requireSpaceAccess(request.calendarSpaceId(), currentUserId);
        validateEnterpriseUsers(space, currentUserId, request.participantUserIds(), null);
        return conflictService.checkConflicts(currentUserId, request);
    }

    private CalendarEvent requireVisibleEvent(long currentUserId, long eventId) {
        CalendarEvent event = eventRepository.findEvent(eventId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "事件不存在"));
        permissionService.requireSpaceAccess(event.calendarSpaceId(), currentUserId);
        if (!"space".equals(event.visibility())
                && event.createdBy() != currentUserId
                && !eventRepository.isParticipant(event.id(), currentUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return event;
    }

    private CalendarEvent requireEventForWrite(long currentUserId, long eventId) {
        CalendarEvent event = eventRepository.findEvent(eventId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "事件不存在"));
        permissionService.requireSpaceAccess(event.calendarSpaceId(), currentUserId);
        return event;
    }

    private EventResponse snapshot(long eventId) {
        return snapshot(eventId, List.of());
    }

    private EventResponse snapshot(long eventId, List<EventConflict> conflicts) {
        CalendarEvent event = eventRepository.findEvent(eventId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "事件不存在"));
        return EventResponse.of(event, eventRepository.findParticipants(eventId), conflicts);
    }

    private EventResponse eventWithConflicts(long currentUserId, CalendarEvent event) {
        List<EventParticipant> participants = eventRepository.findParticipants(event.id());
        List<EventConflict> conflicts = conflictService.detectConflicts(
                currentUserId,
                participants.stream().map(EventParticipant::userId).toList(),
                event.startTime(),
                event.endTime(),
                event.id());
        return EventResponse.of(event, participants, conflicts);
    }

    private void requireConflictConfirmation(List<EventConflict> conflicts, Boolean forceOnConflict) {
        if (conflicts == null || conflicts.isEmpty() || Boolean.TRUE.equals(forceOnConflict)) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requiresConfirmation", true);
        details.put("conflicts", conflicts);
        throw new ApiException(ErrorCode.CONFLICT, "该时间段存在日程冲突，请确认是否继续", details);
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

    private List<Long> participantsToUserIds(List<ParticipantCommand> participants) {
        return participants.stream()
                .map(ParticipantCommand::userId)
                .toList();
    }

    private List<Long> notifiedUserIds(List<Long> participantUserIds, Long ownerUserId, long currentUserId) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        if (participantUserIds != null) {
            userIds.addAll(participantUserIds);
        }
        if (ownerUserId != null) {
            userIds.add(ownerUserId);
        }
        userIds.remove(currentUserId);
        return userIds.stream().toList();
    }

    private Long newlyAssignedOwnerUserId(Long existingOwnerUserId, Long ownerUserId) {
        if (ownerUserId == null || ownerUserId.equals(existingOwnerUserId)) {
            return null;
        }
        return ownerUserId;
    }

    private void notifyAddedParticipants(
            long actorUserId,
            CalendarSpaceAccess space,
            EventResponse event,
            List<Long> participantUserIds) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            return;
        }
        for (Long participantUserId : participantUserIds) {
            long notificationId = reminderRepository.createNotification(new CreateNotificationCommand(
                    participantUserId,
                    event.calendarSpaceId(),
                    null,
                    "event_invite",
                    "你被添加到日程「" + event.title() + "」",
                    eventSummary(space, event),
                    eventInvitePayload(actorUserId, event)));
            NotificationRecord notification = reminderRepository
                    .findNotification(notificationId, participantUserId)
                    .orElse(null);
            if (notification == null) {
                continue;
            }
            NotificationResponse response = NotificationResponse.of(notification);
            if (notificationWebSocketHandler.push(response)) {
                reminderRepository.markNotificationPushed(notificationId, reminderRepository.now());
            }
            operationLogRepository.create(new OperationLogEntry(
                    participantUserId,
                    event.calendarSpaceId(),
                    SYSTEM_SOURCE,
                    "create",
                    NOTIFICATION_TARGET,
                    notificationId,
                    null,
                    response,
                    false));
        }
    }

    private String eventSummary(CalendarSpaceAccess space, EventResponse event) {
        return "空间：" + space.name()
                + "；时间：" + event.startTime() + " - " + event.endTime()
                + (event.location() == null ? "" : "；地点：" + event.location());
    }

    private String eventInvitePayload(long actorUserId, EventResponse event) {
        return """
                {"eventId":%d,"actorUserId":%d,"startTime":"%s","endTime":"%s"}
                """.formatted(event.id(), actorUserId, event.startTime(), event.endTime()).trim();
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
