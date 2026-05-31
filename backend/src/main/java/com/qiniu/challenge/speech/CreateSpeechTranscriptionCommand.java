package com.qiniu.challenge.speech;

import java.math.BigDecimal;

public record CreateSpeechTranscriptionCommand(
        long userId,
        long calendarSpaceId,
        Long conversationId,
        String provider,
        String modelName,
        String transcriptText,
        BigDecimal confidence,
        String audioFormat,
        Integer audioDurationMs,
        String status,
        String errorCode,
        String errorMessage) {
}
