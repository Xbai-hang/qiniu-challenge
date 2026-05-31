package com.qiniu.challenge.speech;

import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.speech.provider", havingValue = "mock", matchIfMissing = true)
public class MockSpeechTranscriptionClient implements SpeechTranscriptionClient {

    @Override
    public SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request) {
        return new SpeechTranscriptionResult(
                "明天上午十点提醒我做项目复盘",
                provider(),
                "mock-stt",
                new BigDecimal("0.9800"),
                audioFormat(request.contentType(), request.filename()),
                null);
    }

    @Override
    public String provider() {
        return "mock";
    }

    private String audioFormat(String contentType, String filename) {
        if (contentType != null && contentType.contains("/")) {
            return contentType.substring(contentType.indexOf('/') + 1);
        }
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1);
        }
        return "webm";
    }
}
