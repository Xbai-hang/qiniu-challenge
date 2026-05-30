package com.qiniu.challenge.ai;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record CreateAiTaskStateCommand(
        long conversationId,
        long userId,
        long calendarSpaceId,
        String taskType,
        String status,
        Map<String, Object> draftPayload,
        List<String> missingFields,
        String riskLevel,
        OffsetDateTime expiresAt) {
}
