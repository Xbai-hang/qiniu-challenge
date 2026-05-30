package com.qiniu.challenge.ai;

import java.time.OffsetDateTime;
import java.util.Map;

public record PendingConfirmationResponse(
        long id,
        long conversationId,
        long calendarSpaceId,
        String actionType,
        String riskLevel,
        String summary,
        Map<String, Object> payload,
        String status,
        OffsetDateTime expiresAt) {
}
