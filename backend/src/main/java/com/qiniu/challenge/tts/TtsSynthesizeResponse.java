package com.qiniu.challenge.tts;

import java.time.OffsetDateTime;

public record TtsSynthesizeResponse(
        long ttsId,
        String audioUrl,
        OffsetDateTime expiresAt) {
}
