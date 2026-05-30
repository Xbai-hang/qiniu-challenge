package com.qiniu.challenge.ai;

import java.util.Map;

public record RiskEvaluationRequest(
        ToolDefinition tool,
        ToolExecutionContext context,
        Map<String, Object> arguments) {
}
