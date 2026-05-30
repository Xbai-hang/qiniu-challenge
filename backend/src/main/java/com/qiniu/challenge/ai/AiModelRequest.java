package com.qiniu.challenge.ai;

import java.util.List;
import java.util.Map;

public record AiModelRequest(
        List<AiModelMessage> messages,
        List<ToolDefinition> tools,
        Map<String, Object> options) {
}
