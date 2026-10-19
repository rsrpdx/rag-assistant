package com.rag.assistant.module.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Configuration
public class LangChainConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Using BgeSmallZhV15EmbeddingModel (local ONNX model)");
        return new BgeSmallZhV15EmbeddingModel();
    }

    @Bean
    @org.springframework.lang.Nullable
    public QdrantClient qdrantClient(QdrantConfig qdrantConfig, EmbeddingModel embeddingModel) {
        try {
            QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(qdrantConfig.getHost(), qdrantConfig.getPort(), false).build();
            QdrantClient client = new QdrantClient(grpcClient);
            ensureCollection(client, qdrantConfig, embeddingModel.dimension());
            return client;
        } catch (Exception e) {
            log.warn("Qdrant connection failed: {}. RAG features will be unavailable.", e.getMessage());
            return null;
        }
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(@org.springframework.lang.Nullable QdrantClient qdrantClient, QdrantConfig qdrantConfig) {
        if (qdrantClient != null) {
            log.info("Using Qdrant as vector store");
            return QdrantEmbeddingStore.builder()
                    .client(qdrantClient)
                    .collectionName(qdrantConfig.getCollection())
                    .build();
        }
        log.warn("Qdrant not available, using InMemoryEmbeddingStore as fallback");
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public OpenAiStreamingChatModel streamingChatModel(LangChainProperties props) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(props.getBaseUrl())
                .apiKey(props.getApiKey())
                .modelName(props.getChatModel())
                .temperature(props.getTemperature())
                .maxTokens(props.getMaxTokens())
                .build();
    }

    private void ensureCollection(QdrantClient client, QdrantConfig cfg, int modelDimension) {
        try {
            List<String> collections = client.listCollectionsAsync().get();
            boolean exists = collections.stream().anyMatch(name -> name.equals(cfg.getCollection()));
            if (!exists) {
                client.createCollectionAsync(
                        cfg.getCollection(),
                        Collections.VectorParams.newBuilder()
                                .setSize(modelDimension)
                                .setDistance(Collections.Distance.Cosine)
                                .build()
                ).get();
                log.info("Created Qdrant collection: {} (dim={})", cfg.getCollection(), modelDimension);
                return;
            }

            if (cfg.getDimension() != modelDimension) {
                log.warn("配置 rag.qdrant.dimension={} 与当前 Embedding 模型维度={} 不一致，请同步修改配置。",
                        cfg.getDimension(), modelDimension);
            }

            long existingSize = client.getCollectionInfoAsync(cfg.getCollection()).get()
                    .getConfig().getParams().getVectorsConfig().getParams().getSize();
            if (existingSize != modelDimension) {
                log.error("Qdrant collection [{}] 的向量维度为 {}，与 Embedding 模型维度 {} 不一致，"
                                + "写入和检索都会失败。请删除该 collection 后重启服务，由程序重建并重新导入文档。",
                        cfg.getCollection(), existingSize, modelDimension);
            } else {
                log.info("Qdrant collection [{}] ready (dim={})", cfg.getCollection(), existingSize);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to initialize Qdrant collection", e);
        }
    }
}