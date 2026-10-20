package com.rag.assistant.thirdparty.minio;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.minio")
public class MinioConfig {

    private String endpoint;
    private String publicEndpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;

    @Bean
    @Primary
    public MinioClient minioClient() {
        return buildClient(endpoint);
    }

    @Bean("publicMinioClient")
    public MinioClient publicMinioClient() {
        String target = (publicEndpoint == null || publicEndpoint.isBlank()) ? endpoint : publicEndpoint;
        return buildClient(target);
    }

    private MinioClient buildClient(String targetEndpoint) {
        return MinioClient.builder()
                .endpoint(targetEndpoint)
                .region("us-east-1")
                .credentials(accessKey, secretKey)
                .build();
    }
}
