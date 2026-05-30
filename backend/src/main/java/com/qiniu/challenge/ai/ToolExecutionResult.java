package com.qiniu.challenge.ai;

public record ToolExecutionResult(
        String toolName,
        RiskLevel riskLevel,
        String status,
        Object data,
        boolean confirmationRequired) {

    public static ToolExecutionResult succeeded(String toolName, RiskLevel riskLevel, Object data) {
        return new ToolExecutionResult(toolName, riskLevel, "succeeded", data, false);
    }

    public static ToolExecutionResult confirmationRequired(String toolName, RiskLevel riskLevel, Object data) {
        return new ToolExecutionResult(toolName, riskLevel, "confirmation_required", data, true);
    }
}
