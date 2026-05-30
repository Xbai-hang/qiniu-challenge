package com.qiniu.challenge.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiChatRequest(
        @NotNull Long calendarSpaceId,
        Long conversationId,
        String inputMode,
        @NotBlank String message) {
}
