package com.qiniu.challenge.event;

import java.util.List;

public interface OperationLogRepository {

    long create(OperationLogEntry entry);

    OperationLogPage findLogs(OperationLogQuery query);

    List<OperationLogRecord> findLogsForExport(OperationLogQuery query);
}
