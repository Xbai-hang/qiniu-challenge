package com.qiniu.challenge.ai;

public record AiToolCallLogEntry(
        long conversationId,
        Long messageId,
        long userId,
        long calendarSpaceId,
        String toolName,
        RiskLevel riskLevel,
        String requiredPermission,
        Object inputPayload) {
}
