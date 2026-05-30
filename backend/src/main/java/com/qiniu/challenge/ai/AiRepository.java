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
}
