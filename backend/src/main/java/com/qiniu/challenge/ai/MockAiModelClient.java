package com.qiniu.challenge.ai;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock")
public class MockAiModelClient implements AiModelClient {

    @Override
    public AiModelResponse chat(AiModelRequest request) {
        throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI_PROVIDER=mock 已禁用，请配置真实模型服务");
    }

    @Override
    public String provider() {
        return "mock";
    }
}
