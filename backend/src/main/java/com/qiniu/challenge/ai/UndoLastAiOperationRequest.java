package com.qiniu.challenge.ai;

import jakarta.validation.constraints.NotNull;

public record UndoLastAiOperationRequest(@NotNull Long calendarSpaceId) {
}
