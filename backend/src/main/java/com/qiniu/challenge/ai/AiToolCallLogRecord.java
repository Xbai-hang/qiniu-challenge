package com.qiniu.challenge.ai;

import java.time.OffsetDateTime;

public record AiToolCallLogRecord(
        long id,
        long conversationId,
        Long messageId,
        long userId,
        long calendarSpaceId,
        String toolName,
        String riskLevel,
        String requiredPermission,
        String inputPayload,
        String outputPayload,
        String status,
        String errorCode,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt) {
}
