package com.qiniu.challenge.tts;

public record TtsAudio(
        byte[] bytes,
        String contentType) {
}
