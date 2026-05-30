package com.qiniu.challenge.ai;

import java.time.OffsetDateTime;

public record AiMessage(
        long id,
        long conversationId,
        long userId,
        String role,
        String inputMode,
        String content,
        String structuredPayload,
        Long transcriptionId,
        String aiPromptVersion,
        String toolSchemaVersion,
        String modelProvider,
        String modelName,
        OffsetDateTime createdAt) {
}
