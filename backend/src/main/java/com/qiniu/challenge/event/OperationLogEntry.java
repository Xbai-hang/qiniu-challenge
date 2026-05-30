package com.qiniu.challenge.event;

import java.time.OffsetDateTime;

public record OperationLogEntry(
        long userId,
        long calendarSpaceId,
        Long conversationId,
        Long toolCallId,
        String operationSource,
        String operationType,
        String targetType,
        Long targetId,
        Object beforeSnapshot,
        Object afterSnapshot,
        boolean undoable,
        OffsetDateTime undoExpiresAt) {

    public OperationLogEntry(
            long userId,
            long calendarSpaceId,
            String operationSource,
            String operationType,
            String targetType,
            Long targetId,
            Object beforeSnapshot,
            Object afterSnapshot,
            boolean undoable) {
        this(
                userId,
                calendarSpaceId,
                null,
                null,
                operationSource,
                operationType,
                targetType,
                targetId,
                beforeSnapshot,
                afterSnapshot,
                undoable,
                null);
    }
}
