package com.qiniu.challenge.speech;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.speech")
public class SpeechProperties {

    private String provider = "mock";
    private String baseUrl;
    private String chatCompletionsUrl;
    private String transcriptionsUrl;
    private String apiKey;
    private String model = "whisper-1";
    private int timeoutSeconds = 30;
    private long maxFileSizeBytes = 10 * 1024 * 1024;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatCompletionsUrl() {
        return chatCompletionsUrl;
    }

    public void setChatCompletionsUrl(String chatCompletionsUrl) {
        this.chatCompletionsUrl = chatCompletionsUrl;
    }

    public String getTranscriptionsUrl() {
        return transcriptionsUrl;
    }

    public void setTranscriptionsUrl(String transcriptionsUrl) {
        this.transcriptionsUrl = transcriptionsUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}
