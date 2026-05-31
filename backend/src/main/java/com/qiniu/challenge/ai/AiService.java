package com.qiniu.challenge.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.event.EventRepository;
import com.qiniu.challenge.event.EventResponse;
import com.qiniu.challenge.event.OperationLogEntry;
import com.qiniu.challenge.event.OperationLogRecord;
import com.qiniu.challenge.event.OperationLogRepository;
import com.qiniu.challenge.event.PermissionService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_REACT_STEPS = 4;
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final AiRepository aiRepository;
    private final PermissionService permissionService;
    private final ToolRegistry toolRegistry;
    private final RiskEvaluator riskEvaluator;
    private final AiModelClient aiModelClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final OperationLogRepository operationLogRepository;
    private final EventRepository eventRepository;

    public AiService(
            AiRepository aiRepository,
            PermissionService permissionService,
            ToolRegistry toolRegistry,
            RiskEvaluator riskEvaluator,
            AiModelClient aiModelClient,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            OperationLogRepository operationLogRepository,
            EventRepository eventRepository) {
        this.aiRepository = aiRepository;
        this.permissionService = permissionService;
        this.toolRegistry = toolRegistry;
        this.riskEvaluator = riskEvaluator;
        this.aiModelClient = aiModelClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.operationLogRepository = operationLogRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public AiConversation createConversation(long currentUserId, AiConversationRequest request) {
        permissionService.requireSpaceAccess(request.calendarSpaceId(), currentUserId);
        long conversationId = aiRepository.createConversation(new CreateAiConversationCommand(
                currentUserId,
                request.calendarSpaceId(),
                blankToNull(request.title()),
                defaultString(request.channel(), "text"),
                aiProperties.getPromptVersion(),
                aiProperties.getToolSchemaVersion(),
                aiModelClient.provider(),
                aiProperties.getModel()));
        return aiRepository.findConversation(conversationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "AI 会话不存在"));
    }

    public List<AiConversation> listConversations(long currentUserId) {
        return aiRepository.findConversations(currentUserId);
    }

    @Transactional
    public void deleteConversation(long currentUserId, long conversationId) {
        aiRepository.deleteConversation(conversationId, currentUserId);
    }

    @Transactional
    public AiMessage addMessage(long currentUserId, long conversationId, AiMessageRequest request) {
        AiConversation conversation = requireConversation(currentUserId, conversationId);
        long messageId = createMessage(
                currentUserId,
                conversation,
                request.role(),
                request.inputMode(),
                request.content(),
                request.structuredPayload());
        return findMessage(currentUserId, conversation.id(), messageId);
    }

    public List<AiMessage> listMessages(long currentUserId, long conversationId) {
        requireConversation(currentUserId, conversationId);
        return aiRepository.findMessages(conversationId, currentUserId);
    }

    public Collection<ToolDefinition> listTools() {
        return toolRegistry.definitions();
    }

    @Transactional
    public AiChatResponse chat(long currentUserId, AiChatRequest request) {
        return chat(currentUserId, request, null);
    }

    @Transactional
    public AiChatResponse chat(long currentUserId, AiChatRequest request, Long transcriptionId) {
        permissionService.requireSpaceAccess(request.calendarSpaceId(), currentUserId);
        AiConversation conversation = request.conversationId() == null
                ? createConversation(currentUserId, new AiConversationRequest(
                        request.calendarSpaceId(),
                        summarizeTitle(request.message()),
                        defaultString(request.inputMode(), "text")))
                : requireConversation(currentUserId, request.conversationId());
        long userMessageId = createMessage(
                currentUserId,
                conversation,
                "user",
                defaultString(request.inputMode(), "text"),
                request.message(),
                null,
                transcriptionId);

        List<ToolExecutionResult> toolResults = new ArrayList<>();
        List<PendingConfirmationResponse> confirmations = new ArrayList<>();
        List<AiTaskStateResponse> taskStates = new ArrayList<>();
        String reply = "";
        Map<String, Object> resultCard = null;

        List<AiModelMessage> reactMessages = modelMessages(currentUserId, conversation.id());
        if (isUndoIntent(request.message())) {
            UndoLastAiOperationResponse undo = undoLast(
                    currentUserId,
                    new UndoLastAiOperationRequest(conversation.calendarSpaceId()));
            Map<String, Object> undoCard = Map.of(
                    "type", "operation_undone",
                    "operationId", undo.operationId(),
                    "actions", List.of("refresh_calendar"));
            long assistantMessageId = createMessage(
                    currentUserId,
                    conversation,
                    "assistant",
                    "text",
                    undo.summary(),
                    undoCard);
            return new AiChatResponse(
                    conversation.id(),
                    assistantMessageId,
                    undo.summary(),
                    undoCard,
                    List.of(),
                    List.of(),
                    List.of());
        }
        boolean calendarToolIntent = isCalendarToolIntent(request.message());
        AiModelResponse modelResponse = aiModelClient.chat(modelRequest(reactMessages, calendarToolIntent));
        reply = blankToNull(modelResponse.content());
        List<AiRequestedToolCall> requestedToolCalls = !calendarToolIntent || modelResponse.toolCalls() == null
                ? List.of()
                : modelResponse.toolCalls();

        for (int step = 0; step < MAX_REACT_STEPS && !requestedToolCalls.isEmpty(); step++) {
            List<AiRequestedToolCall> stepToolCalls = withToolCallIds(requestedToolCalls, step);
            reactMessages.add(AiModelMessage.assistantToolCalls(stepToolCalls));
            for (AiRequestedToolCall toolCall : stepToolCalls) {
                ToolExecutionResult result = executeTool(currentUserId, toolCall.toolName(), new AiToolExecutionRequest(
                        conversation.id(),
                        userMessageId,
                        conversation.calendarSpaceId(),
                        normalizeArguments(toolCall.arguments())));
                toolResults.add(result);
                reactMessages.add(AiModelMessage.toolObservation(
                        toolCall.id(),
                        toolCall.toolName(),
                        toolObservationJson(result)));
                if (result.confirmationRequired()) {
                    confirmations.addAll(aiRepository.findPendingConfirmations(currentUserId).stream()
                            .filter(item -> item.conversationId() == conversation.id())
                            .toList());
                } else {
                    resultCard = resultCardFor(result);
                    if (shouldUseToolReply(reply)) {
                        reply = successReply(result);
                    }
                }
            }
            requestedToolCalls = List.of();
            AiModelResponse finalResponse = aiModelClient.chat(modelRequest(
                    reactMessages,
                    calendarToolIntent && confirmations.isEmpty()));
            if (hasUsefulModelReply(finalResponse.content())) {
                reply = blankToNull(finalResponse.content());
            }
            requestedToolCalls = confirmations.isEmpty() && finalResponse.toolCalls() != null
                    ? finalResponse.toolCalls()
                    : List.of();
        }

        if (!confirmations.isEmpty() && (reply == null || reply.isBlank() || "AI 模型客户端已就绪".equals(reply))) {
            reply = confirmations.get(0).summary();
        }
        if (reply == null || reply.isBlank()) {
            reply = "我已收到你的请求。";
        }

        long assistantMessageId = createMessage(
                currentUserId,
                conversation,
                "assistant",
                "text",
                reply,
                resultCard == null ? Map.of("toolCalls", toolResults) : resultCard);
        return new AiChatResponse(
                conversation.id(),
                assistantMessageId,
                reply,
                resultCard,
                toolResults,
                confirmations,
                taskStates);
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
        long conversationId = request.conversationId() == null
                ? createToolConversation(currentUserId, request.calendarSpaceId())
                : request.conversationId();
        long logId = aiRepository.startToolCall(new AiToolCallLogEntry(
                conversationId,
                request.messageId(),
                currentUserId,
                request.calendarSpaceId(),
                toolName,
                riskLevel,
                tool.definition().requiredPermission(),
                arguments));
        try {
            if (riskLevel.requiresConfirmation()) {
                long confirmationId = aiRepository.createPendingConfirmation(new CreatePendingConfirmationCommand(
                        conversationId,
                        currentUserId,
                        request.calendarSpaceId(),
                        toolName,
                        riskLevel.value(),
                        confirmationSummary(toolName, arguments),
                        arguments,
                        OffsetDateTime.now(DEFAULT_ZONE).plusMinutes(10)));
                ToolExecutionResult result = ToolExecutionResult.confirmationRequired(
                        toolName,
                        riskLevel,
                        Map.of(
                                "confirmationId", confirmationId,
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
            writeAiOperationLogIfNeeded(currentUserId, request.calendarSpaceId(), conversationId, logId, toolName, result);
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

    public List<PendingConfirmationResponse> listConfirmations(long currentUserId) {
        return aiRepository.findPendingConfirmations(currentUserId);
    }

    @Transactional
    public Map<String, Object> confirm(long currentUserId, long confirmationId) {
        PendingConfirmationResponse confirmation = aiRepository.findPendingConfirmation(confirmationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "待确认动作不存在或已过期"));
        ToolExecutionResult result = executeConfirmedTool(currentUserId, confirmation);
        aiRepository.markConfirmation(confirmationId, "confirmed");
        return Map.of(
                "status", "confirmed",
                "resultCard", resultCardFor(result) == null ? Map.of() : resultCardFor(result),
                "toolCall", result);
    }

    @Transactional
    public Map<String, Object> reject(long currentUserId, long confirmationId) {
        PendingConfirmationResponse confirmation = aiRepository.findPendingConfirmation(confirmationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "待确认动作不存在或已过期"));
        aiRepository.markConfirmation(confirmation.id(), "rejected");
        return Map.of("status", "rejected");
    }

    @Transactional
    public UndoLastAiOperationResponse undoLast(long currentUserId, UndoLastAiOperationRequest request) {
        permissionService.requireSpaceAccess(request.calendarSpaceId(), currentUserId);
        OperationLogRecord log = operationLogRepository
                .findLastUndoableAiOperation(currentUserId, request.calendarSpaceId())
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "没有可撤销的 AI 写操作"));
        if (!"event".equals(log.targetType()) || log.targetId() == null) {
            throw new ApiException(ErrorCode.CONFLICT, "该操作暂不支持撤销");
        }
        if ("create".equals(log.operationType())) {
            if (!eventRepository.softDeleteEvent(log.targetId())) {
                throw new ApiException(ErrorCode.CONFLICT, "该事件已被修改或删除，无法安全撤销");
            }
            operationLogRepository.markUndone(log.id());
            operationLogRepository.create(new OperationLogEntry(
                    currentUserId,
                    request.calendarSpaceId(),
                    null,
                    null,
                    "ai",
                    "undo",
                    "event",
                    log.targetId(),
                    readMap(log.afterSnapshot()),
                    null,
                    false,
                    null));
            return new UndoLastAiOperationResponse(true, log.id(), "已撤销刚才创建的日程。");
        }
        if ("delete".equals(log.operationType())) {
            if (!eventRepository.restoreDeletedEvent(log.targetId())) {
                throw new ApiException(ErrorCode.CONFLICT, "该事件已被其他操作修改，无法安全撤销");
            }
            operationLogRepository.markUndone(log.id());
            operationLogRepository.create(new OperationLogEntry(
                    currentUserId,
                    request.calendarSpaceId(),
                    null,
                    null,
                    "ai",
                    "undo",
                    "event",
                    log.targetId(),
                    null,
                    readMap(log.beforeSnapshot()),
                    false,
                    null));
            return new UndoLastAiOperationResponse(true, log.id(), "已撤销刚才删除的日程。");
        }
        throw new ApiException(ErrorCode.CONFLICT, "该操作暂不支持撤销");
    }

    private ToolExecutionResult executeConfirmedTool(long currentUserId, PendingConfirmationResponse confirmation) {
        RegisteredTool tool = toolRegistry.require(confirmation.actionType());
        ToolExecutionContext context = new ToolExecutionContext(
                currentUserId,
                confirmation.calendarSpaceId(),
                confirmation.conversationId(),
                null);
        long logId = aiRepository.startToolCall(new AiToolCallLogEntry(
                confirmation.conversationId(),
                null,
                currentUserId,
                confirmation.calendarSpaceId(),
                confirmation.actionType(),
                RiskLevel.fromValue(confirmation.riskLevel()),
                tool.definition().requiredPermission(),
                confirmation.payload()));
        try {
            ToolExecutionResult executionResult = tool.executor().execute(context, confirmation.payload());
            ToolExecutionResult result = new ToolExecutionResult(
                    confirmation.actionType(),
                    RiskLevel.fromValue(confirmation.riskLevel()),
                    executionResult.status(),
                    executionResult.data(),
                    false);
            aiRepository.finishToolCall(logId, result.status(), result, null, null);
            writeAiOperationLogIfNeeded(
                    currentUserId,
                    confirmation.calendarSpaceId(),
                    confirmation.conversationId(),
                    logId,
                    confirmation.actionType(),
                    result);
            return result;
        } catch (ApiException exception) {
            aiRepository.finishToolCall(logId, "failed", null, exception.errorCode().code(), exception.getMessage());
            throw exception;
        }
    }

    private AiModelRequest modelRequest(List<AiModelMessage> messages, boolean includeTools) {
        return new AiModelRequest(
                messages,
                includeTools ? List.copyOf(toolRegistry.definitions()) : List.of(),
                Map.of());
    }

    private List<AiModelMessage> modelMessages(long currentUserId, long conversationId) {
        List<AiModelMessage> messages = new ArrayList<>();
        messages.add(new AiModelMessage("system", """
                你是语音日历 AI Agent，也可以进行普通闲聊。
                非日历意图直接用中文回答，不要调用工具。
                只有用户明确要创建日程、查询日程、检查冲突、推荐时间、修改或删除日程时，才使用 ReACT 工作流：发起工具 Action，收到工具 Observation 后给出 Final Answer。
                只能通过工具读取或改变日历数据，不要凭空编造日程。
                查询日程调用 list_events 或 search_events；创建明确日程调用 create_event；检查冲突调用 check_conflict；推荐时间应先查询相关时间范围的日程再给建议；删除日程可调用 search_events 或 delete_event，delete_event 是高风险操作。
                查询任意时间段时，把用户表达的日期范围解析成 start/end ISO 8601 参数传给 list_events；例如今天、明天、下周、本月、某一天、某月、某年、从某日到某日都应明确起止时间。
                如果日历操作信息缺少标题、日期或时间，用中文简短追问，不要编造。
                时间使用 ISO 8601，默认时区 Asia/Shanghai。
                最终回答应直接面向用户，不暴露 Thought/Action/Observation 标签。
                """));
        aiRepository.findMessages(conversationId, currentUserId).stream()
                .limit(12)
                .forEach(message -> messages.add(new AiModelMessage(message.role(), message.content())));
        return messages;
    }

    private List<AiRequestedToolCall> withToolCallIds(List<AiRequestedToolCall> toolCalls, int step) {
        List<AiRequestedToolCall> normalized = new ArrayList<>();
        int index = 0;
        for (AiRequestedToolCall toolCall : toolCalls) {
            String id = blankToNull(toolCall.id());
            if (id == null) {
                id = "call_" + step + "_" + index + "_" + Math.abs(toolCall.toolName().hashCode());
            }
            normalized.add(new AiRequestedToolCall(id, toolCall.toolName(), normalizeArguments(toolCall.arguments())));
            index++;
        }
        return normalized;
    }

    private String toolObservationJson(ToolExecutionResult result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "toolName", result.toolName(),
                    "status", result.status(),
                    "confirmationRequired", result.confirmationRequired(),
                    "data", result.data() == null ? Map.of() : result.data()));
        } catch (JsonProcessingException exception) {
            return "{\"status\":\"" + result.status() + "\"}";
        }
    }

    private boolean hasUsefulModelReply(String reply) {
        String normalized = blankToNull(reply);
        return normalized != null && !"AI 模型客户端已就绪".equals(normalized);
    }

    private boolean isUndoIntent(String message) {
        String normalized = message == null ? "" : message.trim();
        return normalized.contains("撤销");
    }

    private boolean isCalendarToolIntent(String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase();
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.matches(".*(删除|取消)\\s*\\d+.*")) {
            return true;
        }
        return normalized.contains("日程")
                || normalized.contains("日历")
                || normalized.contains("会议")
                || normalized.contains("行程")
                || normalized.contains("事项")
                || normalized.contains("安排")
                || normalized.contains("提醒")
                || normalized.contains("冲突")
                || normalized.contains("空闲")
                || normalized.contains("有空")
                || normalized.contains("忙不忙")
                || normalized.contains("推荐时间")
                || normalized.contains("开会时间")
                || normalized.contains("有什么事")
                || normalized.contains("有啥事")
                || normalized.contains("今天")
                || normalized.contains("明天")
                || normalized.contains("后天")
                || normalized.contains("本周")
                || normalized.contains("下周")
                || normalized.contains("本月")
                || normalized.contains("下月")
                || normalized.contains("创建日程")
                || normalized.contains("新建日程")
                || normalized.contains("创建会议")
                || normalized.contains("新建会议")
                || normalized.contains("删除日程")
                || normalized.contains("取消日程")
                || normalized.contains("删除会议")
                || normalized.contains("取消会议")
                || normalized.contains("改到")
                || normalized.contains("推迟")
                || normalized.contains("提前")
                || normalized.contains("schedule")
                || normalized.contains("calendar")
                || normalized.contains("meeting")
                || normalized.contains("event")
                || normalized.contains("availability")
                || normalized.contains("conflict");
    }

    private void writeAiOperationLogIfNeeded(
            long userId,
            long calendarSpaceId,
            long conversationId,
            long toolCallId,
            String toolName,
            ToolExecutionResult result) {
        if (!"succeeded".equals(result.status())) {
            return;
        }
        if ("create_event".equals(toolName) && result.data() instanceof EventResponse event) {
            operationLogRepository.create(new OperationLogEntry(
                    userId,
                    calendarSpaceId,
                    conversationId,
                    toolCallId,
                    "ai",
                    "create",
                    "event",
                    event.id(),
                    null,
                    event,
                    true,
                    OffsetDateTime.now(DEFAULT_ZONE).plusMinutes(10)));
        } else if ("delete_event".equals(toolName)) {
            Long eventId = null;
            if (result.data() instanceof Map<?, ?> data && data.get("eventId") instanceof Number number) {
                eventId = number.longValue();
            }
            operationLogRepository.create(new OperationLogEntry(
                    userId,
                    calendarSpaceId,
                    conversationId,
                    toolCallId,
                    "ai",
                    "delete",
                    "event",
                    eventId,
                    null,
                    result.data(),
                    eventId != null,
                    eventId == null ? null : OffsetDateTime.now(DEFAULT_ZONE).plusMinutes(10)));
        }
    }

    private Map<String, Object> resultCardFor(ToolExecutionResult result) {
        if ("create_event".equals(result.toolName()) && result.data() instanceof EventResponse event) {
            return Map.of(
                    "type", "event_created",
                    "eventId", event.id(),
                    "title", event.title(),
                    "startTime", event.startTime().toString(),
                    "actions", List.of("undo", "view_event"));
        }
        if ("delete_event".equals(result.toolName())) {
            return Map.of(
                    "type", "event_deleted",
                    "actions", List.of("undo"));
        }
        if ("list_events".equals(result.toolName())) {
            return Map.of(
                    "type", "event_list",
                    "actions", List.of("refresh_calendar"));
        }
        return null;
    }

    private String successReply(ToolExecutionResult result) {
        if ("create_event".equals(result.toolName()) && result.data() instanceof EventResponse event) {
            return "已为你创建「" + event.title() + "」。";
        }
        if ("list_events".equals(result.toolName())) {
            return eventListReply(result.data());
        }
        if ("delete_event".equals(result.toolName())) {
            return "操作已完成。";
        }
        return "已完成。";
    }

    private boolean shouldUseToolReply(String reply) {
        return reply == null || reply.isBlank() || "AI 模型客户端已就绪".equals(reply);
    }

    private String confirmationSummary(String toolName, Map<String, Object> arguments) {
        if ("delete_event".equals(toolName)) {
            return "确认删除这个日程吗？确认后我会执行删除，并保留 10 分钟撤销入口。";
        }
        return "这个操作风险较高，请确认后执行。";
    }

    private String eventListReply(Object data) {
        if (!(data instanceof List<?> events) || events.isEmpty()) {
            return "当前范围内没有日程。";
        }
        StringBuilder reply = new StringBuilder("当前范围内有 ").append(events.size()).append(" 个日程：");
        int index = 1;
        for (Object item : events.stream().limit(8).toList()) {
            if (item instanceof EventResponse event) {
                reply.append("\n")
                        .append(index++)
                        .append(". ")
                        .append(event.title())
                        .append("：")
                        .append(formatEventTime(event));
            }
        }
        if (events.size() > 8) {
            reply.append("\n还有 ").append(events.size() - 8).append(" 个日程未展开。");
        }
        return reply.toString();
    }

    private String formatEventTime(EventResponse event) {
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");
        return formatter.format(event.startTime()) + " - " + formatter.format(event.endTime());
    }

    private Map<String, Object> normalizeArguments(Map<String, Object> arguments) {
        return arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
    }

    private long createMessage(
            long currentUserId,
            AiConversation conversation,
            String role,
            String inputMode,
            String content,
            Object structuredPayload) {
        return createMessage(currentUserId, conversation, role, inputMode, content, structuredPayload, null);
    }

    private long createMessage(
            long currentUserId,
            AiConversation conversation,
            String role,
            String inputMode,
            String content,
            Object structuredPayload,
            Long transcriptionId) {
        return aiRepository.createMessage(new CreateAiMessageCommand(
                conversation.id(),
                currentUserId,
                role,
                inputMode,
                content,
                structuredPayload,
                transcriptionId,
                aiProperties.getPromptVersion(),
                aiProperties.getToolSchemaVersion(),
                aiModelClient.provider(),
                aiProperties.getModel()));
    }

    private AiMessage findMessage(long currentUserId, long conversationId, long messageId) {
        return aiRepository.findMessages(conversationId, currentUserId).stream()
                .filter(message -> message.id() == messageId)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "AI 消息不存在"));
    }

    private long createToolConversation(long currentUserId, long calendarSpaceId) {
        return aiRepository.createConversation(new CreateAiConversationCommand(
                currentUserId,
                calendarSpaceId,
                "工具调用",
                "tool",
                aiProperties.getPromptVersion(),
                aiProperties.getToolSchemaVersion(),
                aiModelClient.provider(),
                aiProperties.getModel()));
    }

    private AiConversation requireConversation(long currentUserId, long conversationId) {
        return aiRepository.findConversation(conversationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "AI 会话不存在"));
    }

    private String summarizeTitle(String message) {
        String normalized = blankToNull(message);
        if (normalized == null) {
            return "AI 对话";
        }
        return normalized.length() > 24 ? normalized.substring(0, 24) : normalized;
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

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

}
