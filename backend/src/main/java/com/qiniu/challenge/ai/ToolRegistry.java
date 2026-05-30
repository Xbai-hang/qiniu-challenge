package com.qiniu.challenge.ai;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<RegisteredTool> registeredTools) {
        for (RegisteredTool tool : registeredTools) {
            register(tool);
        }
    }

    public void register(RegisteredTool tool) {
        if (tool == null || tool.definition() == null || tool.definition().name() == null) {
            throw new IllegalArgumentException("Tool definition is required");
        }
        tools.put(tool.definition().name(), tool);
    }

    public RegisteredTool require(String name) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "AI 工具不存在");
        }
        return tool;
    }

    public Collection<ToolDefinition> definitions() {
        return tools.values().stream()
                .map(RegisteredTool::definition)
                .toList();
    }
}
