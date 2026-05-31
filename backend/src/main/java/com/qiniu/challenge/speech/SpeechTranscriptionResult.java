package com.qiniu.challenge.speech;

import java.math.BigDecimal;

public record SpeechTranscriptionResult(
        String text,
        String provider,
        String modelName,
        BigDecimal confidence,
        String audioFormat,
        Integer durationMs) {
}
