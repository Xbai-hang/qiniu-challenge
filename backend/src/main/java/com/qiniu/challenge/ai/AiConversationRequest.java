package com.qiniu.challenge.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiConversationRequest(
        @NotNull
        Long calendarSpaceId,

        @Size(max = 200)
        String title,

        @Size(max = 32)
        String channel) {
}
