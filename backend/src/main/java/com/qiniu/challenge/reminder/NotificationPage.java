package com.qiniu.challenge.reminder;

import java.util.List;

public record NotificationPage(
        List<NotificationResponse> items,
        int page,
        int size,
        long total,
        long unreadCount) {
}
