package com.qiniu.challenge.event;

public record OperationLogQuery(
        long currentUserId,
        Long calendarSpaceId,
        String operationSource,
        String targetType,
        int page,
        int size) {

    public int offset() {
        return Math.max(0, (page - 1) * size);
    }
}
