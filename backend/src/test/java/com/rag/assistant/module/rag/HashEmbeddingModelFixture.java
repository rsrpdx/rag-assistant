package com.rag.assistant.module.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;

/**
 * 改造前的字符 n-gram 哈希向量化实现，保留在测试目录中作为对照基线，
 * 用于量化新旧方案在中文改写提问上的召回差异。
 */
public class HashEmbeddingModelFixture implements EmbeddingModel {

    private static final int VECTOR_DIM = 384;

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<Embedding> embeddings = new ArrayList<>();
        for (TextSegment segment : segments) {
            embeddings.add(embed(segment.text()).content());
        }
        return Response.from(embeddings);
    }

    @Override
    public Response<Embedding> embed(String text) {
        float[] vector = new float[VECTOR_DIM];
        if (text != null && !text.isEmpty()) {
            String normalized = text.toLowerCase().trim();
            for (int n = 1; n <= 3; n++) {
                for (int i = 0; i <= normalized.length() - n; i++) {
                    String gram = normalized.substring(i, i + n);
                    int hash = Math.abs(gram.hashCode());
                    vector[hash % VECTOR_DIM] += 1.0f;
                }
            }
            float magnitude = 0;
            for (float v : vector) magnitude += v * v;
            magnitude = (float) Math.sqrt(magnitude);
            if (magnitude > 0) {
                for (int i = 0; i < VECTOR_DIM; i++) {
                    vector[i] /= magnitude;
                }
            }
        }
        int tokens = text != null ? text.length() : 0;
        return Response.from(new Embedding(vector), new TokenUsage(tokens, tokens));
    }

    @Override
    public int dimension() {
        return VECTOR_DIM;
    }
}