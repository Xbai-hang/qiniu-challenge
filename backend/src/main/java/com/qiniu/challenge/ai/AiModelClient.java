package com.qiniu.challenge.ai;

public interface AiModelClient {

    AiModelResponse chat(AiModelRequest request);

    String provider();
}
