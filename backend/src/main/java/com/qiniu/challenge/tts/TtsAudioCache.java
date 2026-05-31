package com.qiniu.challenge.tts;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TtsAudioCache {

    private final Map<String, CachedAudio> audioByKey = new ConcurrentHashMap<>();

    public void put(String key, TtsAudio audio, OffsetDateTime expiresAt) {
        audioByKey.put(key, new CachedAudio(audio.bytes(), audio.contentType(), expiresAt));
    }

    public Optional<TtsAudio> get(String key) {
        CachedAudio cached = audioByKey.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.expiresAt().isBefore(OffsetDateTime.now(cached.expiresAt().getOffset()))) {
            audioByKey.remove(key);
            return Optional.empty();
        }
        return Optional.of(new TtsAudio(cached.bytes(), cached.contentType()));
    }

    private record CachedAudio(byte[] bytes, String contentType, OffsetDateTime expiresAt) {
    }
}
