package com.qiniu.challenge.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.speech.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleSpeechTranscriptionClient implements SpeechTranscriptionClient {

    private final SpeechProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleSpeechTranscriptionClient(SpeechProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build();
    }

    @Override
    public SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request) {
        if ((!hasText(properties.getBaseUrl()) && !hasText(properties.getTranscriptionsUrl()))
                || !hasText(properties.getApiKey())
                || !hasText(properties.getModel())) {
            throw new ApiException(
                    ErrorCode.SPEECH_SERVICE_UNAVAILABLE,
                    "语音识别配置不完整，请检查 SPEECH_BASE_URL 或 SPEECH_TRANSCRIPTIONS_URL、SPEECH_API_KEY 和 SPEECH_MODEL");
        }
        try {
            String boundary = "----qiniu-speech-" + UUID.randomUUID();
            byte[] body = multipartBody(boundary, request);
            URI uri = transcriptionsUri();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                        ErrorCode.SPEECH_SERVICE_UNAVAILABLE,
                        "语音识别调用失败："
                                + response.statusCode()
                                + "，请检查 SPEECH_TRANSCRIPTIONS_URL 或 SPEECH_BASE_URL 是否指向 OpenAI 兼容的音频转写接口 "
                                + uri.getPath()
                                + responseBodyHint(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("text").asText("");
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
        return "openai-compatible";
    }

    private URI transcriptionsUri() {
        if (hasText(properties.getTranscriptionsUrl())) {
            return URI.create(properties.getTranscriptionsUrl().trim().replaceAll("/+$", ""));
        }
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        if (baseUrl.endsWith("/audio/transcriptions")) {
            return URI.create(baseUrl);
        }
        if (baseUrl.endsWith("/v1")) {
            return URI.create(baseUrl + "/audio/transcriptions");
        }
        return URI.create(baseUrl + "/v1/audio/transcriptions");
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

    private byte[] multipartBody(String boundary, SpeechTranscriptionRequest request) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writePart(output, boundary, "model", null, null, properties.getModel().getBytes(StandardCharsets.UTF_8));
        writePart(output, boundary, "response_format", null, null, "json".getBytes(StandardCharsets.UTF_8));
        writePart(
                output,
                boundary,
                "file",
                hasText(request.filename()) ? request.filename() : "audio.webm",
                hasText(request.contentType()) ? request.contentType() : "audio/webm",
                request.audio());
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private void writePart(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String filename,
            String contentType,
            byte[] value) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) {
            disposition += "; filename=\"" + filename.replace("\"", "") + "\"";
        }
        output.write((disposition + "\r\n").getBytes(StandardCharsets.UTF_8));
        if (contentType != null) {
            output.write(("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(value);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String audioFormat(String contentType, String filename) {
        if (contentType != null && contentType.contains("/")) {
            return contentType.substring(contentType.indexOf('/') + 1);
        }
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1);
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
