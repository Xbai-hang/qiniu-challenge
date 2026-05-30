package com.qiniu.challenge.ai;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiModelClient implements AiModelClient {

    @Override
    public AiModelResponse chat(AiModelRequest request) {
        return new AiModelResponse(provider(), "mock", "AI 模型客户端已就绪", List.of());
    }

    @Override
    public String provider() {
        return "mock";
    }
}
