package com.qiniu.challenge.ai;

import java.time.OffsetDateTime;
import java.util.Map;

public record CreatePendingConfirmationCommand(
        long conversationId,
        long userId,
        long calendarSpaceId,
        String actionType,
        String riskLevel,
        String summary,
        Map<String, Object> payload,
        OffsetDateTime expiresAt) {
}
