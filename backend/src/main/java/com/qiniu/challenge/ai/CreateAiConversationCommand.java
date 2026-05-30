package com.qiniu.challenge.ai;

public record CreateAiConversationCommand(
        long userId,
        long calendarSpaceId,
        String title,
        String channel,
        String aiPromptVersion,
        String toolSchemaVersion,
        String modelProvider,
        String modelName) {
}
