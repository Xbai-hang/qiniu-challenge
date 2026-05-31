package com.qiniu.challenge.speech;

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
@ConditionalOnProperty(name = "app.speech.provider", havingValue = "openai-chat-completions")
public class OpenAiChatCompletionsSpeechTranscriptionClient implements SpeechTranscriptionClient {

    private static final String TRANSCRIPTION_PROMPT = """
            请将用户上传的音频完整转写为文字。
            只输出转写文本，不要解释，不要添加标点修饰说明，不要输出 Markdown。
            如果音频中没有可识别语音，只输出空字符串。
            """;

    private final SpeechProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiChatCompletionsSpeechTranscriptionClient(SpeechProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build();
    }

    @Override
    public SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request) {
        if ((!hasText(properties.getBaseUrl()) && !hasText(properties.getChatCompletionsUrl()))
                || !hasText(properties.getApiKey())
                || !hasText(properties.getModel())) {
            throw new ApiException(
                    ErrorCode.SPEECH_SERVICE_UNAVAILABLE,
                    "语音识别配置不完整，请检查 SPEECH_BASE_URL 或 SPEECH_CHAT_COMPLETIONS_URL、SPEECH_API_KEY 和 SPEECH_MODEL");
        }
        try {
            URI uri = chatCompletionsUri();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("messages", List.of(
                    Map.of(
                            "role", "system",
                            "content", TRANSCRIPTION_PROMPT),
                    Map.of(
                            "role", "user",
                            "content", List.of(audioContent(request)))));
            payload.put("stream", false);
            payload.put("asr_options", Map.of("enable_itn", false));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                        ErrorCode.SPEECH_SERVICE_UNAVAILABLE,
                        "语音识别调用失败："
                                + response.statusCode()
                                + "，请检查 SPEECH_CHAT_COMPLETIONS_URL 或 SPEECH_BASE_URL 是否指向 OpenAI 兼容的聊天接口 "
                                + uri.getPath()
                                + responseBodyHint(response.body()));
            }
            String text = transcriptionText(response.body()).trim();
            if (!hasText(text)) {
                throw new ApiException(ErrorCode.SPEECH_SERVICE_UNAVAILABLE, "语音识别结果为空");
            }
            return new SpeechTranscriptionResult(
                    text,
                    provider(),
                    properties.getModel(),
                    null,
                    audioFormat(request.contentType(), request.filename()),
                    null);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.SPEECH_SERVICE_UNAVAILABLE, "语音识别不可用：" + exception.getMessage());
        }
    }

    @Override
    public String provider() {
        return "openai-chat-completions";
    }

    private Map<String, Object> audioContent(SpeechTranscriptionRequest request) {
        String contentType = audioContentType(request.contentType(), request.filename());
        String dataUrl = "data:" + contentType + ";base64,"
                + Base64.getEncoder().encodeToString(request.audio());
        return Map.of(
                "type", "input_audio",
                "input_audio", Map.of("data", dataUrl));
    }

    private String transcriptionText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.path("choices").path(0).path("message");
        JsonNode content = message.path("content");
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode node : content) {
                String part = node.path("text").asText("");
                if (hasText(part)) {
                    text.append(part);
                }
            }
            return text.toString();
        }
        return "";
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

    private String audioFormat(String contentType, String filename) {
        if (contentType != null && contentType.contains("/")) {
            return normalizeAudioFormat(contentType.substring(contentType.indexOf('/') + 1));
        }
        if (filename != null && filename.contains(".")) {
            return normalizeAudioFormat(filename.substring(filename.lastIndexOf('.') + 1));
        }
        return null;
    }

    private String audioContentType(String contentType, String filename) {
        if (hasText(contentType) && contentType.startsWith("audio/")) {
            return contentType;
        }
        String format = audioFormat(contentType, filename);
        if ("mp3".equals(format)) {
            return "audio/mpeg";
        }
        if (hasText(format)) {
            return "audio/" + format;
        }
        return "audio/webm";
    }

    private String normalizeAudioFormat(String value) {
        if (value == null) {
            return null;
        }
        String format = value.toLowerCase().replaceAll("[^a-z0-9].*$", "");
        if ("mpeg".equals(format)) {
            return "mp3";
        }
        return format;
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
