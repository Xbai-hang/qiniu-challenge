package com.qiniu.challenge.ai;

public class OllamaAiModelClient implements AiModelClient {

    @Override
    public AiModelResponse chat(AiModelRequest request) {
        throw new UnsupportedOperationException("Ollama client is a PR 059 adapter skeleton");
    }

    @Override
    public String provider() {
        return "ollama";
    }
}
