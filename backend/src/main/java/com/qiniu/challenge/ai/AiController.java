package com.qiniu.challenge.ai;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
