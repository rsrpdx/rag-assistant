package com.rag.assistant.module.rag.service;

import com.rag.assistant.module.rag.config.QdrantConfig;
import com.rag.assistant.module.rag.model.RetrievedChunk;
import dev.langchain4j.data.embedding.Embedding;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量检索服务。
 *
 * 这里直接用 Qdrant 客户端发起检索，而不是复用 langchain4j 的 QdrantEmbeddingStore.search：
 * 当前项目使用的 qdrant 客户端（1.13.0）无法解析 Qdrant 服务端（1.18.2）回传的向量数据，
 * 检索命中后取到的向量长度为 0，CosineSimilarity 会直接抛异常。
 * Qdrant 本身已经返回了正确的相似度分数，因此这里只取 payload 文本与 score。
 */
@Slf4j
@Service
public class QdrantSearchService {

    /** 必须与向量写入时使用的 payload 键保持一致（QdrantEmbeddingStore 默认值） */
    public static final String PAYLOAD_TEXT_KEY = "text_segment";

    private final QdrantClient qdrantClient;
    private final EmbeddingService embeddingService;
    private final String collectionName;
    private final int maxResults;
    private final double minScore;

    public QdrantSearchService(
            ObjectProvider<QdrantClient> qdrantClientProvider,
            QdrantConfig qdrantConfig,
            EmbeddingService embeddingService,
            @Value("${rag.search.max-results:5}") int maxResults,
            @Value("${rag.search.min-score:0.5}") double minScore) {
        this.qdrantClient = qdrantClientProvider.getIfAvailable();
        this.collectionName = qdrantConfig.getCollection();
        this.embeddingService = embeddingService;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public List<RetrievedChunk> search(String query) {
        if (qdrantClient == null) {
            log.warn("Qdrant client not available, search skipped");
            return List.of();
        }

        Embedding queryEmbedding = embeddingService.embed(query);
        try {
            List<Points.ScoredPoint> points = qdrantClient.searchAsync(Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(toFloatList(queryEmbedding.vector()))
                    .setLimit(maxResults)
                    .setScoreThreshold((float) minScore)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build()).get();

            List<RetrievedChunk> chunks = new ArrayList<>(points.size());
            for (Points.ScoredPoint point : points) {
                if (point.getScore() < minScore) {
                    continue;
                }
                Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = point.getPayloadMap();
                io.qdrant.client.grpc.JsonWithInt.Value textValue = payload.get(PAYLOAD_TEXT_KEY);
                if (textValue == null || textValue.getStringValue().isEmpty()) {
                    log.warn("Point {} has no text payload, skipped", pointId(point.getId()));
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>();
                payload.forEach((key, value) -> {
                    if (!PAYLOAD_TEXT_KEY.equals(key)) {
                        metadata.put(key, toJavaObject(value));
                    }
                });
                chunks.add(new RetrievedChunk(pointId(point.getId()), textValue.getStringValue(), metadata, point.getScore()));
            }
            log.debug("Search returned {} results", chunks.size());
            return chunks;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant search interrupted", e);
        } catch (Exception e) {
            log.error("Qdrant search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static String pointId(Points.PointId pointId) {
        String uuid = pointId.getUuid();
        return uuid == null || uuid.isEmpty() ? String.valueOf(pointId.getNum()) : uuid;
    }

    private static Object toJavaObject(io.qdrant.client.grpc.JsonWithInt.Value value) {
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return value.getStringValue();
            case INTEGER_VALUE:
                return value.getIntegerValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            default:
                return value.toString();
        }
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }
}