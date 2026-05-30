package com.qiniu.challenge.ai;

import java.util.List;

public record AiModelResponse(
        String provider,
        String model,
        String content,
        List<AiRequestedToolCall> toolCalls) {
}
