package com.qiniu.challenge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleAiModelClient implements AiModelClient {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleAiModelClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build();
    }

    @Override
    public AiModelResponse chat(AiModelRequest request) {
        if (!hasText(properties.getBaseUrl()) || !hasText(properties.getApiKey()) || !hasText(properties.getModel())) {
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 模型配置不完整，请检查 AI_BASE_URL、AI_API_KEY 和 AI_MODEL");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("messages", request.messages().stream()
                    .map(message -> Map.of(
                            "role", message.role(),
                            "content", message.content()))
                    .toList());
            payload.put("temperature", properties.getTemperature());
            if (request.tools() != null && !request.tools().isEmpty()) {
                payload.put("tools", request.tools().stream().map(this::toOpenAiTool).toList());
                payload.put("tool_choice", "auto");
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(chatCompletionsUri())
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 服务调用失败：" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText("");
            List<AiRequestedToolCall> toolCalls = readToolCalls(message.path("tool_calls"));
            return new AiModelResponse(provider(), properties.getModel(), content, toolCalls);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 服务不可用：" + exception.getMessage());
        }
    }

    @Override
    public String provider() {
        return "openai-compatible";
    }

    private URI chatCompletionsUri() {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        if (baseUrl.endsWith("/chat/completions")) {
            return URI.create(baseUrl);
        }
        if (baseUrl.endsWith("/v1")) {
            return URI.create(baseUrl + "/chat/completions");
        }
        return URI.create(baseUrl + "/v1/chat/completions");
    }

    private Map<String, Object> toOpenAiTool(ToolDefinition definition) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", definition.name(),
                        "description", definition.description(),
                        "parameters", normalizeSchema(definition.inputSchema())));
    }

    private Map<String, Object> normalizeSchema(Map<String, Object> schema) {
        Object propertiesNode = schema == null ? null : schema.get("properties");
        if (!(propertiesNode instanceof List<?> fields)) {
            return schema == null ? Map.of("type", "object", "properties", Map.of()) : schema;
        }
        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        for (Object field : fields) {
            if (field != null) {
                propertiesMap.put(field.toString(), Map.of("type", "string"));
            }
        }
        return Map.of(
                "type", "object",
                "properties", propertiesMap,
                "additionalProperties", true);
    }

    private List<AiRequestedToolCall> readToolCalls(JsonNode toolCallNodes) throws Exception {
        if (!toolCallNodes.isArray()) {
            return List.of();
        }
        List<AiRequestedToolCall> toolCalls = new ArrayList<>();
        for (JsonNode node : toolCallNodes) {
            JsonNode function = node.path("function");
            String name = function.path("name").asText(null);
            if (!hasText(name)) {
                continue;
            }
            Map<String, Object> arguments = Map.of();
            String rawArguments = function.path("arguments").asText("");
            if (hasText(rawArguments)) {
                arguments = objectMapper.readValue(
                        rawArguments,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
            }
            toolCalls.add(new AiRequestedToolCall(name, arguments));
        }
        return toolCalls;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
