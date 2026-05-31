package com.qiniu.challenge.tts;

import java.time.OffsetDateTime;

public record CreateTtsCacheCommand(
        long userId,
        Long messageId,
        String provider,
        String voice,
        String textHash,
        String audioUrl,
        String storageKey,
        String status,
        OffsetDateTime expiresAt) {
}
