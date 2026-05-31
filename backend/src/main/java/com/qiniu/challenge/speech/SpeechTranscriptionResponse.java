package com.qiniu.challenge.speech;

import java.math.BigDecimal;

public record SpeechTranscriptionResponse(
        long transcriptionId,
        String text,
        String provider,
        BigDecimal confidence,
        Integer durationMs) {
}
