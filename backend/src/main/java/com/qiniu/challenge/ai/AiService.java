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
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
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
                null);

        List<ToolExecutionResult> toolResults = new ArrayList<>();
        List<PendingConfirmationResponse> confirmations = new ArrayList<>();
        List<AiTaskStateResponse> taskStates = new ArrayList<>();
        String reply = "";
        Map<String, Object> resultCard = null;

        List<AiRequestedToolCall> requestedToolCalls = List.of();
        InferredAction inferredAction = inferAction(request.message());
        if ("undo_last_ai_operation".equals(inferredAction.taskType())) {
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
        try {
            AiModelResponse modelResponse = aiModelClient.chat(modelRequest(currentUserId, conversation.id(), request.message()));
            reply = blankToNull(modelResponse.content());
            requestedToolCalls = modelResponse.toolCalls() == null ? List.of() : modelResponse.toolCalls();
        } catch (ApiException exception) {
            if (exception.errorCode() != ErrorCode.AI_SERVICE_UNAVAILABLE || !"mock".equals(aiModelClient.provider())) {
                throw exception;
            }
        }

        if (requestedToolCalls.isEmpty()) {
            InferredAction inferred = inferredAction;
            if (inferred.missingFields().isEmpty()) {
                requestedToolCalls = inferred.toolCall() == null ? List.of() : List.of(inferred.toolCall());
            } else {
                long taskId = aiRepository.createTaskState(new CreateAiTaskStateCommand(
                        conversation.id(),
                        currentUserId,
                        conversation.calendarSpaceId(),
                        inferred.taskType(),
                        "collecting_info",
                        inferred.draftPayload(),
                        inferred.missingFields(),
                        null,
                        OffsetDateTime.now(DEFAULT_ZONE).plusHours(2)));
                taskStates = aiRepository.findOpenTaskStates(currentUserId, conversation.id()).stream()
                        .filter(task -> task.id() == taskId)
                        .toList();
                reply = missingFieldsReply(inferred.missingFields());
            }
        }

        for (AiRequestedToolCall toolCall : requestedToolCalls) {
            ToolExecutionResult result = executeTool(currentUserId, toolCall.toolName(), new AiToolExecutionRequest(
                    conversation.id(),
                    userMessageId,
                    conversation.calendarSpaceId(),
                    normalizeArguments(toolCall.arguments())));
            toolResults.add(result);
            if (result.confirmationRequired()) {
                confirmations.addAll(aiRepository.findPendingConfirmations(currentUserId).stream()
                        .filter(item -> item.conversationId() == conversation.id())
                        .toList());
            } else {
                resultCard = resultCardFor(result);
                if (shouldUseToolReply(result, reply)) {
                    reply = successReply(result);
                }
            }
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

    private AiModelRequest modelRequest(long currentUserId, long conversationId, String currentMessage) {
        List<AiModelMessage> messages = new ArrayList<>();
        messages.add(new AiModelMessage("system", """
                你是语音日历 AI Agent。只能通过工具改变日历数据。
                如果用户要查询日程，调用 list_events 或 search_events。
                如果用户要创建明确日程，调用 create_event。
                如果用户要删除日程，调用 search_events 或 delete_event；delete_event 是高风险操作。
                如果信息缺少标题、日期或时间，用中文简短追问，不要编造。
                时间使用 ISO 8601，默认时区 Asia/Shanghai。
                """));
        aiRepository.findMessages(conversationId, currentUserId).stream()
                .limit(12)
                .forEach(message -> messages.add(new AiModelMessage(message.role(), message.content())));
        messages.add(new AiModelMessage("user", currentMessage));
        return new AiModelRequest(messages, List.copyOf(toolRegistry.definitions()), Map.of());
    }

    private InferredAction inferAction(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.contains("撤销")) {
            return new InferredAction("undo_last_ai_operation", null, Map.of(), List.of(), null);
        }
        if (normalized.contains("删除") || normalized.contains("删掉")) {
            Long eventId = firstLong(normalized);
            if (eventId == null) {
                return new InferredAction(
                        "delete_event",
                        null,
                        Map.of("rawText", normalized),
                        List.of("eventId"),
                        null);
            }
            return new InferredAction(
                    "delete_event",
                    new AiRequestedToolCall("delete_event", Map.of("eventId", eventId)),
                    Map.of("eventId", eventId),
                    List.of(),
                    null);
        }
        if (normalized.contains("创建") || normalized.contains("安排") || normalized.contains("提醒我") || normalized.contains("新建")) {
            Map<String, Object> payload = inferCreatePayload(normalized);
            List<String> missing = new ArrayList<>();
            if (!payload.containsKey("title")) {
                missing.add("title");
            }
            if (!payload.containsKey("startTime")) {
                missing.add("startTime");
            }
            if (!payload.containsKey("endTime")) {
                missing.add("endTime");
            }
            return new InferredAction(
                    "create_event",
                    missing.isEmpty() ? new AiRequestedToolCall("create_event", payload) : null,
                    payload,
                    missing,
                    null);
        }
        OffsetDateTime now = OffsetDateTime.now(DEFAULT_ZONE);
        return new InferredAction(
                "list_events",
                new AiRequestedToolCall("list_events", Map.of(
                        "start", now.toLocalDate().atStartOfDay(DEFAULT_ZONE).toOffsetDateTime().toString(),
                        "end", now.toLocalDate().plusDays(1).atStartOfDay(DEFAULT_ZONE).toOffsetDateTime().toString())),
                Map.of(),
                List.of(),
                null);
    }

    private Map<String, Object> inferCreatePayload(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String title = message
                .replace("帮我", "")
                .replace("提醒我", "")
                .replace("创建", "")
                .replace("新建", "")
                .replace("安排", "")
                .trim();
        if (!title.isBlank()) {
            payload.put("title", title.length() > 60 ? title.substring(0, 60) : title);
        }
        Optional<OffsetDateTime> start = inferStartTime(message);
        start.ifPresent(value -> {
            payload.put("startTime", value.toString());
            payload.put("endTime", value.plusHours(1).toString());
        });
        return payload;
    }

    private Optional<OffsetDateTime> inferStartTime(String message) {
        OffsetDateTime base = OffsetDateTime.now(DEFAULT_ZONE);
        if (message.contains("明天")) {
            base = base.plusDays(1);
        } else if (message.contains("后天")) {
            base = base.plusDays(2);
        } else if (!message.contains("今天")) {
            return Optional.empty();
        }
        int hour = -1;
        if (message.contains("上午")) {
            hour = firstHour(message, 9);
        } else if (message.contains("下午")) {
            hour = firstHour(message, 3);
            if (hour < 12) {
                hour += 12;
            }
        } else if (message.contains("晚上")) {
            hour = firstHour(message, 8);
            if (hour < 12) {
                hour += 12;
            }
        } else {
            hour = firstHour(message, -1);
        }
        if (hour < 0) {
            return Optional.empty();
        }
        return Optional.of(base.withHour(hour).withMinute(0).withSecond(0).withNano(0));
    }

    private int firstHour(String message, int defaultHour) {
        Long number = firstLong(message);
        if (number != null && number >= 0 && number <= 23) {
            return number.intValue();
        }
        Map<String, Integer> zh = new LinkedHashMap<>();
        zh.put("一", 1);
        zh.put("二", 2);
        zh.put("两", 2);
        zh.put("三", 3);
        zh.put("四", 4);
        zh.put("五", 5);
        zh.put("六", 6);
        zh.put("七", 7);
        zh.put("八", 8);
        zh.put("九", 9);
        zh.put("十", 10);
        for (Map.Entry<String, Integer> entry : zh.entrySet()) {
            if (message.contains(entry.getKey() + "点")) {
                return entry.getValue();
            }
        }
        return defaultHour;
    }

    private Long firstLong(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group());
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

    private boolean shouldUseToolReply(ToolExecutionResult result, String reply) {
        if ("list_events".equals(result.toolName())) {
            return true;
        }
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

    private String missingFieldsReply(List<String> missingFields) {
        List<String> labels = missingFields.stream()
                .map(field -> switch (field) {
                    case "title" -> "主题";
                    case "startTime" -> "开始时间";
                    case "endTime" -> "结束时间";
                    case "eventId" -> "具体日程";
                    default -> field;
                })
                .toList();
        return "还需要补充：" + String.join("、", labels) + "。";
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
        return aiRepository.createMessage(new CreateAiMessageCommand(
                conversation.id(),
                currentUserId,
                role,
                inputMode,
                content,
                structuredPayload,
                null,
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

    private record InferredAction(
            String taskType,
            AiRequestedToolCall toolCall,
            Map<String, Object> draftPayload,
            List<String> missingFields,
            String riskLevel) {
    }
}
