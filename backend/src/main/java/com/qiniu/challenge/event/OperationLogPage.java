package com.qiniu.challenge.event;

import java.util.List;

public record OperationLogPage(
        List<OperationLogRecord> items,
        int page,
        int size,
        long total) {
}
