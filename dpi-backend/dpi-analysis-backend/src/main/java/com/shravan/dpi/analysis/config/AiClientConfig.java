package com.shravan.dpi.analysis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiClientConfig {

    @Value("${ai.huggingface.endpoint:https://api-inference.huggingface.co/models}")
    private String aiEndpoint;

    @Value("${ai.huggingface.model:meta-llama/Llama-2-7b-chat-hf}")
    private String aiModel;

    @Value("${ai.huggingface.api.key:}")
    private String apiKey;

    @Value("${ai.huggingface.timeout:30000}")
    private int timeout;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getAiEndpoint() {
        return aiEndpoint;
    }

    public void setAiEndpoint(String aiEndpoint) {
        this.aiEndpoint = aiEndpoint;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
