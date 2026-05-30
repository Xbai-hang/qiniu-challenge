package com.qiniu.challenge.ai;

import java.time.OffsetDateTime;

public record AiConversation(
        long id,
        long userId,
        long calendarSpaceId,
        String title,
        String channel,
        String aiPromptVersion,
        String toolSchemaVersion,
        String modelProvider,
        String modelName,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
