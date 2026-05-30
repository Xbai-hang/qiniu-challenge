package com.qiniu.challenge.ai;

import java.util.List;
import java.util.Map;

public record AiChatResponse(
        long conversationId,
        long messageId,
        String reply,
        Map<String, Object> resultCard,
        List<ToolExecutionResult> toolCalls,
        List<PendingConfirmationResponse> confirmations,
        List<AiTaskStateResponse> taskStates) {
}
