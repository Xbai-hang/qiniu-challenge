package com.qiniu.challenge.ai;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AiModelClient.class)
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
