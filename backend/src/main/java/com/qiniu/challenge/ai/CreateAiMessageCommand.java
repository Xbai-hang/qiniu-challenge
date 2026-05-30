package com.qiniu.challenge.ai;

public record CreateAiMessageCommand(
        long conversationId,
        long userId,
        String role,
        String inputMode,
        String content,
        Object structuredPayload,
        Long transcriptionId,
        String aiPromptVersion,
        String toolSchemaVersion,
        String modelProvider,
        String modelName) {
}
