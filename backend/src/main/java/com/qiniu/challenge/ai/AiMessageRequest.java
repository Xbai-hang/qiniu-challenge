package com.qiniu.challenge.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiMessageRequest(
        @NotBlank
        @Size(max = 32)
        String role,

        @Size(max = 32)
        String inputMode,

        @NotBlank
        String content,

        Object structuredPayload,

        Long transcriptionId) {
}
