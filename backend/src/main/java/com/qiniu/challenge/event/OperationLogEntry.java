package com.qiniu.challenge.event;

public record OperationLogEntry(
        long userId,
        long calendarSpaceId,
        String operationSource,
        String operationType,
        String targetType,
        Long targetId,
        Object beforeSnapshot,
        Object afterSnapshot,
        boolean undoable) {
}
