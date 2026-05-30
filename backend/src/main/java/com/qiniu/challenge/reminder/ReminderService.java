package com.qiniu.challenge.reminder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.event.CalendarEvent;
import com.qiniu.challenge.event.CalendarSpaceAccess;
import com.qiniu.challenge.event.EventRepository;
import com.qiniu.challenge.event.OperationLogEntry;
import com.qiniu.challenge.event.OperationLogRepository;
import com.qiniu.challenge.event.PermissionService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {

    private static final String MANUAL_SOURCE = "manual";
    private static final String SYSTEM_SOURCE = "system";
    private static final String REMINDER_TARGET = "reminder";
    private static final String NOTIFICATION_TARGET = "notification";

    private final ReminderRepository reminderRepository;
    private final EventRepository eventRepository;
    private final PermissionService permissionService;
    private final OperationLogRepository operationLogRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final ObjectMapper objectMapper;
    private final int dueBatchSize;

    public ReminderService(
            ReminderRepository reminderRepository,
            EventRepository eventRepository,
            PermissionService permissionService,
            OperationLogRepository operationLogRepository,
            NotificationWebSocketHandler notificationWebSocketHandler,
            ObjectMapper objectMapper,
            @Value("${app.reminders.due-batch-size:50}") int dueBatchSize) {
        this.reminderRepository = reminderRepository;
        this.eventRepository = eventRepository;
        this.permissionService = permissionService;
        this.operationLogRepository = operationLogRepository;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.objectMapper = objectMapper;
        this.dueBatchSize = Math.max(1, Math.min(dueBatchSize, 200));
    }

    @Transactional
    public EventReminderResponse createReminder(long currentUserId, long eventId, CreateReminderRequest request) {
        CalendarEvent event = requireVisibleEvent(currentUserId, eventId);
        CalendarSpaceAccess space = permissionService.requireSpaceAccess(event.calendarSpaceId(), currentUserId);
        long targetUserId = request.userId() == null ? currentUserId : request.userId();
        requireReminderTargetAllowed(space, event, currentUserId, targetUserId);
        Integer offsetMinutes = normalizeOffset(request.offsetMinutes(), request.triggerAt());
        OffsetDateTime triggerAt = request.triggerAt() == null
                ? event.startTime().minusMinutes(offsetMinutes == null ? 0 : offsetMinutes)
                : request.triggerAt();

        long reminderId = reminderRepository.createReminder(new CreateReminderCommand(
                event.id(),
                event.calendarSpaceId(),
                targetUserId,
                offsetMinutes,
                triggerAt,
                null,
                currentUserId));
        EventReminderResponse created = EventReminderResponse.of(requireReminder(reminderId));
        operationLogRepository.create(new OperationLogEntry(
                currentUserId,
                event.calendarSpaceId(),
                MANUAL_SOURCE,
                "create",
                REMINDER_TARGET,
                reminderId,
                null,
                created,
                false));
        return created;
    }

    public List<EventReminderResponse> listEventReminders(long currentUserId, long eventId) {
        requireVisibleEvent(currentUserId, eventId);
        return reminderRepository.findRemindersByEvent(eventId).stream()
                .filter(reminder -> reminder.userId() == currentUserId || canManageReminder(currentUserId, reminder))
                .map(EventReminderResponse::of)
                .toList();
    }

    @Transactional
    public EventReminderResponse updateReminder(long currentUserId, long reminderId, UpdateReminderRequest request) {
        EventReminder existing = requireOwnedOrManageableReminder(currentUserId, reminderId);
        CalendarEvent event = requireVisibleEvent(currentUserId, existing.eventId());
        EventReminderResponse beforeSnapshot = EventReminderResponse.of(existing);
        Integer offsetMinutes = normalizeOffset(request.offsetMinutes(), request.triggerAt());
        OffsetDateTime triggerAt = request.triggerAt() == null
                ? event.startTime().minusMinutes(offsetMinutes == null ? 0 : offsetMinutes)
                : request.triggerAt();
        if (!reminderRepository.updateReminder(reminderId, offsetMinutes, triggerAt)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "提醒不存在或不可修改");
        }
        EventReminderResponse afterSnapshot = EventReminderResponse.of(requireReminder(reminderId));
        operationLogRepository.create(new OperationLogEntry(
                currentUserId,
                existing.calendarSpaceId(),
                MANUAL_SOURCE,
                "update",
                REMINDER_TARGET,
                reminderId,
                beforeSnapshot,
                afterSnapshot,
                false));
        return afterSnapshot;
    }

    @Transactional
    public boolean cancelReminder(long currentUserId, long reminderId) {
        EventReminder existing = requireOwnedOrManageableReminder(currentUserId, reminderId);
        EventReminderResponse beforeSnapshot = EventReminderResponse.of(existing);
        if (!reminderRepository.cancelReminder(reminderId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "提醒不存在或已取消");
        }
        EventReminderResponse afterSnapshot = EventReminderResponse.of(requireReminder(reminderId));
        operationLogRepository.create(new OperationLogEntry(
                currentUserId,
                existing.calendarSpaceId(),
                MANUAL_SOURCE,
                "cancel",
                REMINDER_TARGET,
                reminderId,
                beforeSnapshot,
                afterSnapshot,
                false));
        return true;
    }

    @Transactional
    public SnoozeReminderResponse snoozeReminder(long currentUserId, long reminderId, SnoozeReminderRequest request) {
        EventReminder existing = requireOwnedOrManageableReminder(currentUserId, reminderId);
        int minutes = request.minutes() == null ? 10 : request.minutes();
        EventReminderResponse beforeSnapshot = EventReminderResponse.of(existing);
        if (!reminderRepository.markReminderSnoozed(reminderId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "提醒不存在或不可稍后提醒");
        }
        long newReminderId = reminderRepository.createReminder(new CreateReminderCommand(
                existing.eventId(),
                existing.calendarSpaceId(),
                existing.userId(),
                minutes,
                reminderRepository.now().plusMinutes(minutes),
                existing.id(),
                currentUserId));
        EventReminderResponse afterSnapshot = EventReminderResponse.of(requireReminder(newReminderId));
        operationLogRepository.create(new OperationLogEntry(
                currentUserId,
                existing.calendarSpaceId(),
                MANUAL_SOURCE,
                "snooze",
                REMINDER_TARGET,
                newReminderId,
                beforeSnapshot,
                afterSnapshot,
                false));
        return new SnoozeReminderResponse(newReminderId == existing.id() ? reminderId : existing.id(),
                newReminderId,
                afterSnapshot.triggerAt(),
                afterSnapshot.status());
    }

    public NotificationPage listNotifications(long currentUserId, String status, Integer page, Integer size) {
        int normalizedPage = page == null ? 1 : Math.max(1, page);
        int normalizedSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        String normalizedStatus = blankToNull(status);
        List<NotificationResponse> items = reminderRepository
                .findNotifications(currentUserId, normalizedStatus, normalizedPage, normalizedSize)
                .stream()
                .map(NotificationResponse::of)
                .toList();
        return new NotificationPage(
                items,
                normalizedPage,
                normalizedSize,
                reminderRepository.countNotifications(currentUserId, normalizedStatus),
                reminderRepository.countUnreadNotifications(currentUserId));
    }

    @Transactional
    public NotificationResponse markNotificationRead(long currentUserId, long notificationId) {
        NotificationRecord existing = reminderRepository.findNotification(notificationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "通知不存在"));
        reminderRepository.markNotificationRead(notificationId, currentUserId);
        NotificationRecord updated = reminderRepository.findNotification(notificationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "通知不存在"));
        operationLogRepository.create(new OperationLogEntry(
                currentUserId,
                updated.calendarSpaceId(),
                MANUAL_SOURCE,
                "read",
                NOTIFICATION_TARGET,
                notificationId,
                NotificationResponse.of(existing),
                NotificationResponse.of(updated),
                false));
        return NotificationResponse.of(updated);
    }

    @Scheduled(fixedDelayString = "${app.reminders.scan-fixed-delay-ms:30000}")
    @Transactional
    public void scanDueReminders() {
        List<EventReminder> dueReminders = reminderRepository.findDueReminders(reminderRepository.now(), dueBatchSize);
        for (EventReminder reminder : dueReminders) {
            if (!reminderRepository.markReminderSent(reminder.id())) {
                continue;
            }
            CalendarEvent event = eventRepository.findEvent(reminder.eventId()).orElse(null);
            if (event == null) {
                continue;
            }
            long notificationId = reminderRepository.createNotification(new CreateNotificationCommand(
                    reminder.userId(),
                    reminder.calendarSpaceId(),
                    reminder.id(),
                    "reminder",
                    event.title() + " 即将开始",
                    "日程「" + event.title() + "」将在 " + event.startTime() + " 开始",
                    notificationPayload(reminder, event)));
            NotificationRecord notification = reminderRepository
                    .findNotification(notificationId, reminder.userId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "通知不存在"));
            NotificationResponse response = NotificationResponse.of(notification);
            if (notificationWebSocketHandler.push(response)) {
                reminderRepository.markNotificationPushed(notificationId, reminderRepository.now());
            }
            operationLogRepository.create(new OperationLogEntry(
                    reminder.userId(),
                    reminder.calendarSpaceId(),
                    SYSTEM_SOURCE,
                    "create",
                    NOTIFICATION_TARGET,
                    notificationId,
                    null,
                    response,
                    false));
        }
    }

    private EventReminder requireOwnedOrManageableReminder(long currentUserId, long reminderId) {
        EventReminder reminder = requireReminder(reminderId);
        if (reminder.userId() == currentUserId || canManageReminder(currentUserId, reminder)) {
            return reminder;
        }
        throw new ApiException(ErrorCode.FORBIDDEN, "无权操作该提醒");
    }

    private boolean canManageReminder(long currentUserId, EventReminder reminder) {
        CalendarEvent event = eventRepository.findEvent(reminder.eventId()).orElse(null);
        if (event == null) {
            return false;
        }
        CalendarSpaceAccess space = permissionService.requireSpaceAccess(event.calendarSpaceId(), currentUserId);
        try {
            permissionService.requireCanUpdateEvent(space, event, currentUserId);
            return true;
        } catch (ApiException exception) {
            return false;
        }
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

    private void requireReminderTargetAllowed(
            CalendarSpaceAccess space,
            CalendarEvent event,
            long currentUserId,
            long targetUserId) {
        if (space.personal()) {
            if (targetUserId == currentUserId) {
                return;
            }
            throw new ApiException(ErrorCode.FORBIDDEN, "个人提醒只能创建给自己");
        }
        if (targetUserId == currentUserId) {
            return;
        }
        permissionService.requireCanUpdateEvent(space, event, currentUserId);
        if (space.organizationId() == null || !eventRepository.areActiveOrganizationMembers(
                space.organizationId(),
                List.of(targetUserId))) {
            throw new ApiException(ErrorCode.FORBIDDEN, "提醒接收人必须是组织成员");
        }
    }

    private EventReminder requireReminder(long reminderId) {
        return reminderRepository.findReminder(reminderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "提醒不存在"));
    }

    private Integer normalizeOffset(Integer offsetMinutes, OffsetDateTime triggerAt) {
        if (offsetMinutes == null && triggerAt == null) {
            return 15;
        }
        return offsetMinutes;
    }

    private String notificationPayload(EventReminder reminder, CalendarEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.id());
        payload.put("reminderId", reminder.id());
        payload.put("startTime", event.startTime());
        payload.put("endTime", event.endTime());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
