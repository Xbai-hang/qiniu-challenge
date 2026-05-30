package com.qiniu.challenge.ai;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record AiToolExecutionRequest(
        Long conversationId,
        Long messageId,

        @NotNull
        Long calendarSpaceId,

        Map<String, Object> arguments) {
}
