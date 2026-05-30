package com.qiniu.challenge.ai;

import java.util.List;
import java.util.Optional;

public interface AiRepository {

    long createConversation(CreateAiConversationCommand command);

    Optional<AiConversation> findConversation(long conversationId, long userId);

    List<AiConversation> findConversations(long userId);

    long createMessage(CreateAiMessageCommand command);

    List<AiMessage> findMessages(long conversationId, long userId);

    long startToolCall(AiToolCallLogEntry entry);

    void finishToolCall(long logId, String status, Object outputPayload, String errorCode, String errorMessage);

    AiToolCallLogPage findToolCallLogs(
            long currentUserId,
            Long conversationId,
            Long calendarSpaceId,
            int page,
            int size);

    long createTaskState(CreateAiTaskStateCommand command);

    List<AiTaskStateResponse> findOpenTaskStates(long userId, long conversationId);

    long createPendingConfirmation(CreatePendingConfirmationCommand command);

    Optional<PendingConfirmationResponse> findPendingConfirmation(long confirmationId, long userId);

    List<PendingConfirmationResponse> findPendingConfirmations(long userId);

    void markConfirmation(long confirmationId, String status);
}
