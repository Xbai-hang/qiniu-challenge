package com.qiniu.challenge.ai;

public record ToolExecutionContext(
        long userId,
        long calendarSpaceId,
        Long conversationId,
        Long messageId) {
}
