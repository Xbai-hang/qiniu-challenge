package com.qiniu.challenge.ai;

public class OpenAiCompatibleAiModelClient implements AiModelClient {

    @Override
    public AiModelResponse chat(AiModelRequest request) {
        throw new UnsupportedOperationException("OpenAI compatible client is a PR 059 adapter skeleton");
    }

    @Override
    public String provider() {
        return "openai-compatible";
    }
}
