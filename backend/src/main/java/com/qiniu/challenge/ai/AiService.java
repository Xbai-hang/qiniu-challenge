package com.qiniu.challenge.ai;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.event.PermissionService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {

    private final AiRepository aiRepository;
    private final PermissionService permissionService;
    private final ToolRegistry toolRegistry;
    private final RiskEvaluator riskEvaluator;
    private final String aiPromptVersion;
    private final String toolSchemaVersion;

    public AiService(
            AiRepository aiRepository,
            PermissionService permissionService,
            ToolRegistry toolRegistry,
            RiskEvaluator riskEvaluator,
            @Value("${app.ai.prompt-version:prompt-v1}") String aiPromptVersion,
            @Value("${app.ai.tool-schema-version:tool-schema-v1}") String toolSchemaVersion) {
        this.aiRepository = aiRepository;
        this.permissionService = permissionService;
        this.toolRegistry = toolRegistry;
        this.riskEvaluator = riskEvaluator;
        this.aiPromptVersion = aiPromptVersion;
        this.toolSchemaVersion = toolSchemaVersion;
    }

    @Transactional
    public AiConversation createConversation(long currentUserId, AiConversationRequest request) {
        permissionService.requireSpaceAccess(request.calendarSpaceId(), currentUserId);
        long conversationId = aiRepository.createConversation(new CreateAiConversationCommand(
                currentUserId,
                request.calendarSpaceId(),
                blankToNull(request.title()),
                defaultString(request.channel(), "text"),
                aiPromptVersion,
                toolSchemaVersion,
                null,
                null));
        return aiRepository.findConversation(conversationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "AI 会话不存在"));
    }

    public java.util.List<AiConversation> listConversations(long currentUserId) {
        return aiRepository.findConversations(currentUserId);
    }

    @Transactional
    public AiMessage addMessage(long currentUserId, long conversationId, AiMessageRequest request) {
        AiConversation conversation = requireConversation(currentUserId, conversationId);
        long messageId = aiRepository.createMessage(new CreateAiMessageCommand(
                conversation.id(),
                currentUserId,
                request.role(),
                request.inputMode(),
                request.content(),
                request.structuredPayload(),
                request.transcriptionId(),
                aiPromptVersion,
                toolSchemaVersion,
                conversation.modelProvider(),
                conversation.modelName()));
        return aiRepository.findMessages(conversationId, currentUserId).stream()
                .filter(message -> message.id() == messageId)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "AI 消息不存在"));
    }

    public java.util.List<AiMessage> listMessages(long currentUserId, long conversationId) {
        requireConversation(currentUserId, conversationId);
        return aiRepository.findMessages(conversationId, currentUserId);
    }

    public java.util.Collection<ToolDefinition> listTools() {
        return toolRegistry.definitions();
    }

    public ToolExecutionResult executeTool(long currentUserId, String toolName, AiToolExecutionRequest request) {
        permissionService.requireSpaceAccess(request.calendarSpaceId(), currentUserId);
        if (request.conversationId() != null) {
            requireConversation(currentUserId, request.conversationId());
        }
        RegisteredTool tool = toolRegistry.require(toolName);
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        ToolExecutionContext context = new ToolExecutionContext(
                currentUserId,
                request.calendarSpaceId(),
                request.conversationId(),
                request.messageId());
        RiskLevel riskLevel = riskEvaluator.evaluate(new RiskEvaluationRequest(tool.definition(), context, arguments));
        long logId = aiRepository.startToolCall(new AiToolCallLogEntry(
                request.conversationId() == null ? createToolConversation(currentUserId, request.calendarSpaceId()) : request.conversationId(),
                request.messageId(),
                currentUserId,
                request.calendarSpaceId(),
                toolName,
                riskLevel,
                tool.definition().requiredPermission(),
                arguments));
        try {
            if (riskLevel.requiresConfirmation()) {
                ToolExecutionResult result = ToolExecutionResult.confirmationRequired(
                        toolName,
                        riskLevel,
                        Map.of(
                                "toolName", toolName,
                                "riskLevel", riskLevel.value(),
                                "arguments", arguments));
                aiRepository.finishToolCall(logId, "confirmation_required", result, null, null);
                return result;
            }
            ToolExecutionResult executionResult = tool.executor().execute(context, arguments);
            ToolExecutionResult result = new ToolExecutionResult(
                    toolName,
                    riskLevel,
                    executionResult.status(),
                    executionResult.data(),
                    executionResult.confirmationRequired());
            aiRepository.finishToolCall(logId, result.status(), result, null, null);
            return result;
        } catch (ApiException exception) {
            aiRepository.finishToolCall(logId, "failed", null, exception.errorCode().code(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            aiRepository.finishToolCall(logId, "failed", null, ErrorCode.INTERNAL_ERROR.code(), exception.getMessage());
            throw exception;
        }
    }

    public AiToolCallLogPage listToolCallLogs(
            long currentUserId,
            Long conversationId,
            Long calendarSpaceId,
            Integer page,
            Integer size) {
        return aiRepository.findToolCallLogs(
                currentUserId,
                conversationId,
                calendarSpaceId,
                page == null ? 1 : page,
                size == null ? 20 : size);
    }

    private long createToolConversation(long currentUserId, long calendarSpaceId) {
        return aiRepository.createConversation(new CreateAiConversationCommand(
                currentUserId,
                calendarSpaceId,
                "工具调用",
                "tool",
                aiPromptVersion,
                toolSchemaVersion,
                null,
                null));
    }

    private AiConversation requireConversation(long currentUserId, long conversationId) {
        return aiRepository.findConversation(conversationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "AI 会话不存在"));
    }

    private String defaultString(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
