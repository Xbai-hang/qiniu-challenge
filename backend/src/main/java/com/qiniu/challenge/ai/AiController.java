package com.qiniu.challenge.ai;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/conversations")
    public ApiResponse<AiConversation> createConversation(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody AiConversationRequest request) {
        return ApiResponse.success(aiService.createConversation(principal.userId(), request));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AiConversation>> listConversations(
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ApiResponse.success(aiService.listConversations(principal.userId()));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<AiMessage> addMessage(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conversationId,
            @Valid @RequestBody AiMessageRequest request) {
        return ApiResponse.success(aiService.addMessage(principal.userId(), conversationId, request));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<AiMessage>> listMessages(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long conversationId) {
        return ApiResponse.success(aiService.listMessages(principal.userId(), conversationId));
    }

    @GetMapping("/tools")
    public ApiResponse<Collection<ToolDefinition>> listTools() {
        return ApiResponse.success(aiService.listTools());
    }

    @PostMapping("/tools/{toolName}/execute")
    public ApiResponse<ToolExecutionResult> executeTool(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable String toolName,
            @Valid @RequestBody AiToolExecutionRequest request) {
        return ApiResponse.success(aiService.executeTool(principal.userId(), toolName, request));
    }

    @GetMapping("/tool-call-logs")
    public ApiResponse<AiToolCallLogPage> listToolCallLogs(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) Long calendarSpaceId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(aiService.listToolCallLogs(
                principal.userId(),
                conversationId,
                calendarSpaceId,
                page,
                size));
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success(aiService.chat(principal.userId(), request));
    }

    @GetMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CrossOrigin
    public SseEmitter streamChat(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam Long calendarSpaceId,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(defaultValue = "text") String inputMode,
            @RequestParam String message) {
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            emitter.send(SseEmitter.event()
                    .name("message_delta")
                    .data(Map.of("content", "正在理解你的请求")));
            AiChatResponse response = aiService.chat(
                    principal.userId(),
                    new AiChatRequest(calendarSpaceId, conversationId, inputMode, message));
            for (ToolExecutionResult toolCall : response.toolCalls()) {
                emitter.send(SseEmitter.event()
                        .name("tool_call_result")
                        .data(toolCall));
            }
            for (PendingConfirmationResponse confirmation : response.confirmations()) {
                emitter.send(SseEmitter.event()
                        .name("confirmation_required")
                        .data(confirmation));
            }
            emitter.send(SseEmitter.event()
                    .name("final_result")
                    .data(response));
            emitter.complete();
        } catch (Exception exception) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", exception.getMessage())));
            } catch (Exception ignored) {
                // SseEmitter may already be closed by the client.
            }
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @GetMapping("/confirmations")
    public ApiResponse<List<PendingConfirmationResponse>> listConfirmations(
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ApiResponse.success(aiService.listConfirmations(principal.userId()));
    }

    @PostMapping("/confirmations/{confirmationId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long confirmationId) {
        return ApiResponse.success(aiService.confirm(principal.userId(), confirmationId));
    }

    @PostMapping("/confirmations/{confirmationId}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long confirmationId) {
        return ApiResponse.success(aiService.reject(principal.userId(), confirmationId));
    }

    @PostMapping("/undo-last")
    public ApiResponse<UndoLastAiOperationResponse> undoLast(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody UndoLastAiOperationRequest request) {
        return ApiResponse.success(aiService.undoLast(principal.userId(), request));
    }
}
