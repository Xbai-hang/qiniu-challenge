package com.qiniu.challenge.tts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleTextToSpeechClient implements TextToSpeechClient {

    private final TtsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleTextToSpeechClient(TtsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build();
    }

    @Override
    public TtsAudio synthesize(TtsSynthesisRequest request) {
        if (!hasText(properties.getBaseUrl()) || !hasText(properties.getApiKey()) || !hasText(properties.getModel())) {
            throw new ApiException(
                    ErrorCode.TTS_SERVICE_UNAVAILABLE,
                    "TTS 配置不完整，请检查 TTS_BASE_URL、TTS_API_KEY 和 TTS_MODEL");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("input", request.text());
            payload.put("voice", hasText(request.voice()) ? request.voice() : properties.getVoice());
            payload.put("response_format", "mp3");
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(speechUri())
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(ErrorCode.TTS_SERVICE_UNAVAILABLE, "TTS 调用失败：" + response.statusCode());
            }
            String contentType = response.headers()
                    .firstValue("content-type")
                    .orElse("audio/mpeg");
            return new TtsAudio(response.body(), contentType);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.TTS_SERVICE_UNAVAILABLE, "TTS 不可用：" + exception.getMessage());
        }
    }

    @Override
    public String provider() {
        return "openai-compatible";
    }

    private URI speechUri() {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        if (baseUrl.endsWith("/audio/speech")) {
            return URI.create(baseUrl);
        }
        if (baseUrl.endsWith("/v1")) {
            return URI.create(baseUrl + "/audio/speech");
        }
        return URI.create(baseUrl + "/v1/audio/speech");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
