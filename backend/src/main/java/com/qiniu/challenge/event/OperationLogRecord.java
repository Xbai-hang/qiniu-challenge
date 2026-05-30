package com.qiniu.challenge.event;

import java.time.OffsetDateTime;

public record OperationLogRecord(
        long id,
        long userId,
        String userDisplayName,
        long calendarSpaceId,
        String calendarSpaceName,
        String operationSource,
        String operationType,
        String targetType,
        Long targetId,
        String beforeSnapshot,
        String afterSnapshot,
        boolean undoable,
        boolean undone,
        OffsetDateTime undoExpiresAt,
        OffsetDateTime createdAt) {
}
