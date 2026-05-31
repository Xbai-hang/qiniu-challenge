package com.qiniu.challenge.speech;

public record SpeechTranscriptionRequest(
        byte[] audio,
        String filename,
        String contentType) {
}
