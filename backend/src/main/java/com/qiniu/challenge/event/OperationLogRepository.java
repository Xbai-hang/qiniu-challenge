package com.qiniu.challenge.event;

import java.util.List;
import java.util.Optional;

public interface OperationLogRepository {

    long create(OperationLogEntry entry);

    OperationLogPage findLogs(OperationLogQuery query);

    List<OperationLogRecord> findLogsForExport(OperationLogQuery query);

    Optional<OperationLogRecord> findLastUndoableAiOperation(long userId, long calendarSpaceId);

    void markUndone(long operationId);
}
