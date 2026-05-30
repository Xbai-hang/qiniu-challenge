package com.qiniu.challenge.reminder;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping("/api/events/{eventId}/reminders")
    public ApiResponse<EventReminderResponse> createReminder(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long eventId,
            @Valid @RequestBody CreateReminderRequest request) {
        return ApiResponse.success(reminderService.createReminder(principal.userId(), eventId, request));
    }

    @GetMapping("/api/events/{eventId}/reminders")
    public ApiResponse<List<EventReminderResponse>> listEventReminders(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long eventId) {
        return ApiResponse.success(reminderService.listEventReminders(principal.userId(), eventId));
    }

    @PatchMapping("/api/reminders/{reminderId}")
    public ApiResponse<EventReminderResponse> updateReminder(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long reminderId,
            @Valid @RequestBody UpdateReminderRequest request) {
        return ApiResponse.success(reminderService.updateReminder(principal.userId(), reminderId, request));
    }

    @PostMapping("/api/reminders/{reminderId}/cancel")
    public ApiResponse<Boolean> cancelReminder(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long reminderId) {
        return ApiResponse.success(reminderService.cancelReminder(principal.userId(), reminderId));
    }

    @PostMapping("/api/reminders/{reminderId}/snooze")
    public ApiResponse<SnoozeReminderResponse> snoozeReminder(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long reminderId,
            @Valid @RequestBody SnoozeReminderRequest request) {
        return ApiResponse.success(reminderService.snoozeReminder(principal.userId(), reminderId, request));
    }

    @GetMapping("/api/notifications")
    public ApiResponse<NotificationPage> listNotifications(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(reminderService.listNotifications(principal.userId(), status, page, size));
    }

    @PostMapping("/api/notifications/{notificationId}/read")
    public ApiResponse<NotificationResponse> markNotificationRead(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long notificationId) {
        return ApiResponse.success(reminderService.markNotificationRead(principal.userId(), notificationId));
    }
}
