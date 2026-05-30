package com.qiniu.challenge.reminder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository {

    long createReminder(CreateReminderCommand command);

    Optional<EventReminder> findReminder(long reminderId);

    List<EventReminder> findRemindersByEvent(long eventId);

    boolean updateReminder(long reminderId, Integer offsetMinutes, OffsetDateTime triggerAt);

    boolean cancelReminder(long reminderId);

    boolean markReminderSent(long reminderId);

    boolean markReminderSnoozed(long reminderId);

    List<EventReminder> findDueReminders(OffsetDateTime now, int limit);

    long createNotification(CreateNotificationCommand command);

    Optional<NotificationRecord> findNotification(long notificationId, long userId);

    List<NotificationRecord> findNotifications(long userId, String status, int page, int size);

    long countNotifications(long userId, String status);

    long countUnreadNotifications(long userId);

    boolean markNotificationRead(long notificationId, long userId);

    boolean markNotificationPushed(long notificationId, OffsetDateTime pushedAt);

    OffsetDateTime now();
}
