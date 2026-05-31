package com.qiniu.challenge.tts;

import jakarta.validation.constraints.NotBlank;

public record TtsSynthesizeRequest(
        Long messageId,
        @NotBlank String text,
        String voice) {
}
