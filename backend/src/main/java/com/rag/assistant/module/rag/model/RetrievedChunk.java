package com.rag.assistant.module.rag.model;

import java.util.Map;

/**
 * 一次向量检索命中的文档片段。
 * 相似度分数直接采用 Qdrant 返回的 score（余弦相似度），不依赖服务端回传向量数据。
 */
public record RetrievedChunk(String pointId, String text, Map<String, Object> metadata, double score) {
}