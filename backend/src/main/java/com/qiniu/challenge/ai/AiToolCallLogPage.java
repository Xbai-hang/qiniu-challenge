package com.qiniu.challenge.ai;

import java.util.List;

public record AiToolCallLogPage(
        List<AiToolCallLogRecord> items,
        int page,
        int size,
        long total) {
}
