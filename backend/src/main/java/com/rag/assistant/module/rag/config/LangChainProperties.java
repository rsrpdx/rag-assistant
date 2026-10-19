package com.rag.assistant.module.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "rag.llm")
public class LangChainProperties {

    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String embeddingModel;
    private double temperature;
    private int maxTokens;
}
