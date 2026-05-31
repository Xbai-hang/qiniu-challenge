package com.qiniu.challenge.ai;

import java.util.List;

public record AiModelMessage(
        String role,
        String content,
        String name,
        String toolCallId,
        List<AiRequestedToolCall> toolCalls) {

    public AiModelMessage(String role, String content) {
        this(role, content, null, null, List.of());
    }

    public static AiModelMessage assistantToolCalls(List<AiRequestedToolCall> toolCalls) {
        return new AiModelMessage("assistant", "", null, null, toolCalls == null ? List.of() : toolCalls);
    }

    public static AiModelMessage toolObservation(String toolCallId, String toolName, String content) {
        return new AiModelMessage("tool", content, null, toolCallId, List.of());
    }
}
