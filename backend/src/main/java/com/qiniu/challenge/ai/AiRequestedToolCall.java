package com.qiniu.challenge.ai;

import java.util.Map;

public record AiRequestedToolCall(
        String toolName,
        Map<String, Object> arguments) {
}
