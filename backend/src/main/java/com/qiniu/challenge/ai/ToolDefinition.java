package com.qiniu.challenge.ai;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema,
        RiskLevel baseRiskLevel,
        String requiredPermission) {
}
