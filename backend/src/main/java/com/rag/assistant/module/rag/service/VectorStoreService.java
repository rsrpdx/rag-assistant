package com.rag.assistant.module.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class VectorStoreService {

    private final EmbeddingStore<TextSegment> embeddingStore;

    public VectorStoreService(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    public List<String> store(List<TextSegment> segments, List<Embedding> embeddings) {
        if (embeddingStore == null) {
            log.warn("Embedding store not available, vector storage skipped");
            return List.of();
        }
        List<String> pointIds = segments.stream()
                .map(s -> UUID.randomUUID().toString())
                .toList();
        embeddingStore.addAll(pointIds, embeddings, segments);
        log.debug("Stored {} vectors", pointIds.size());
        return pointIds;
    }

    public void remove(Collection<String> pointIds) {
        if (embeddingStore == null || pointIds == null || pointIds.isEmpty()) {
            return;
        }
        try {
            embeddingStore.removeAll(pointIds);
            log.debug("Removed {} vectors from store", pointIds.size());
        } catch (Exception e) {
            log.warn("Failed to remove vectors from store: {}", e.getMessage());
        }
    }
}
