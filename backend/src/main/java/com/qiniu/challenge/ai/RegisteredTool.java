package com.qiniu.challenge.ai;

public record RegisteredTool(
        ToolDefinition definition,
        ToolExecutor executor) {
}
