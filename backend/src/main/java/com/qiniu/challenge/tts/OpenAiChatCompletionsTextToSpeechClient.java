package com.qiniu.challenge.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "openai-chat-completions")
public class OpenAiChatCompletionsTextToSpeechClient implements TextToSpeechClient {

    private final TtsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final List<String> mimoVoices = List.of("mimo_default", "冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean");

    public OpenAiChatCompletionsTextToSpeechClient(TtsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build();
    }

    @Override
    public TtsAudio synthesize(TtsSynthesisRequest request) {
        if ((!hasText(properties.getBaseUrl()) && !hasText(properties.getChatCompletionsUrl()))
                || !hasText(properties.getApiKey())
                || !hasText(properties.getModel())) {
            throw new ApiException(
                    ErrorCode.TTS_SERVICE_UNAVAILABLE,
                    "TTS 配置不完整，请检查 TTS_BASE_URL 或 TTS_CHAT_COMPLETIONS_URL、TTS_API_KEY 和 TTS_MODEL");
        }
        try {
            URI uri = chatCompletionsUri();
            String voice = normalizedVoice(request.voice());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("messages", List.of(Map.of(
                    "role", "assistant",
                    "content", request.text())));
            payload.put("audio", Map.of(
                    "format", "wav",
                    "voice", voice));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                        ErrorCode.TTS_SERVICE_UNAVAILABLE,
                        "TTS 调用失败："
                                + response.statusCode()
                                + "，请检查 TTS_CHAT_COMPLETIONS_URL 或 TTS_BASE_URL 是否指向 OpenAI 兼容的聊天接口 "
                                + uri.getPath()
                                + responseBodyHint(response.body()));
            }
            String audioData = audioData(response.body());
            if (!hasText(audioData)) {
                throw new ApiException(ErrorCode.TTS_SERVICE_UNAVAILABLE, "TTS 返回音频为空");
            }
            return new TtsAudio(Base64.getDecoder().decode(audioData), "audio/wav");
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.TTS_SERVICE_UNAVAILABLE, "TTS 不可用：" + exception.getMessage());
        }
    }

    @Override
    public String provider() {
        return "openai-chat-completions";
    }

    private String audioData(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").path(0).path("message").path("audio").path("data").asText("");
    }

    private String normalizedVoice(String requestedVoice) {
        String fallbackVoice = hasText(properties.getVoice()) ? properties.getVoice().trim() : "mimo_default";
        String voice = hasText(requestedVoice) ? requestedVoice.trim() : fallbackVoice;
        if (isMimoModel() && !mimoVoices.contains(voice)) {
            return mimoVoices.contains(fallbackVoice) ? fallbackVoice : "mimo_default";
        }
        return voice;
    }

    private boolean isMimoModel() {
        return hasText(properties.getModel()) && properties.getModel().startsWith("mimo-");
    }

    private URI chatCompletionsUri() {
        if (hasText(properties.getChatCompletionsUrl())) {
            return URI.create(properties.getChatCompletionsUrl().trim().replaceAll("/+$", ""));
        }
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        if (baseUrl.endsWith("/chat/completions")) {
            return URI.create(baseUrl);
        }
        if (baseUrl.endsWith("/v1")) {
            return URI.create(baseUrl + "/chat/completions");
        }
        return URI.create(baseUrl + "/v1/chat/completions");
    }

    private String responseBodyHint(String body) {
        if (!hasText(body)) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.length() > 180) {
            compact = compact.substring(0, 180) + "...";
        }
        return "，服务端返回：" + compact;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
