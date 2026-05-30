package com.qiniu.challenge.ai;

import java.util.Map;

public interface ToolExecutor {

    ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments);
}
