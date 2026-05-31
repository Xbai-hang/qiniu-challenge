package com.qiniu.challenge.tts;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TtsService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final TextToSpeechClient textToSpeechClient;
    private final TtsRepository ttsRepository;
    private final TtsAudioCache audioCache;
    private final TtsProperties properties;

    public TtsService(
            TextToSpeechClient textToSpeechClient,
            TtsRepository ttsRepository,
            TtsAudioCache audioCache,
            TtsProperties properties) {
        this.textToSpeechClient = textToSpeechClient;
        this.ttsRepository = ttsRepository;
        this.audioCache = audioCache;
        this.properties = properties;
    }

    @Transactional
    public TtsSynthesizeResponse synthesize(long currentUserId, TtsSynthesizeRequest request) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "TTS 文本不能为空");
        }
        if (text.length() > properties.getMaxTextLength()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "TTS 文本过长");
        }
        if (request.messageId() != null && !ttsRepository.messageBelongsToUser(request.messageId(), currentUserId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "AI 消息不存在");
        }
        String voice = hasText(request.voice()) ? request.voice().trim() : properties.getVoice();
        TtsAudio audio = textToSpeechClient.synthesize(new TtsSynthesisRequest(text, voice));
        OffsetDateTime expiresAt = OffsetDateTime.now(DEFAULT_ZONE).plusMinutes(Math.max(1, properties.getCacheTtlMinutes()));
        String storageKey = "tts:" + currentUserId + ":" + sha256(text + "|" + voice + "|" + System.nanoTime());
        long ttsId = ttsRepository.create(new CreateTtsCacheCommand(
                currentUserId,
                request.messageId(),
                textToSpeechClient.provider(),
                voice,
                sha256(text),
                null,
                storageKey,
                "ready",
                expiresAt));
        audioCache.put(storageKey, audio, expiresAt);
        return new TtsSynthesizeResponse(ttsId, "/api/tts/audio/" + ttsId, expiresAt);
    }

    public TtsAudio audio(long currentUserId, long ttsId) {
        TtsCacheEntry entry = ttsRepository.findActive(ttsId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "TTS 音频不存在或已过期"));
        return audioCache.get(entry.storageKey())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "TTS 音频缓存已过期，请重新生成"));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.TTS_SERVICE_UNAVAILABLE, "TTS 缓存键生成失败");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
