package com.rag.assistant.module.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.qdrant")
public class QdrantConfig {

    private String host;
    private int port;
    private String collection;
    private int dimension;
}
