package com.qiniu.challenge.ai;

import java.util.Map;

public record AiRequestedToolCall(
        String id,
        String toolName,
        Map<String, Object> arguments) {

    public AiRequestedToolCall(String toolName, Map<String, Object> arguments) {
        this(null, toolName, arguments);
    }
}
