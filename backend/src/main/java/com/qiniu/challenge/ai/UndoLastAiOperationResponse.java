package com.qiniu.challenge.ai;

public record UndoLastAiOperationResponse(
        boolean undone,
        long operationId,
        String summary) {
}
