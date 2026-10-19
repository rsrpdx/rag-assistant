package com.rag.assistant.module.rag;

import com.rag.assistant.module.rag.config.QdrantConfig;
import com.rag.assistant.module.rag.model.RetrievedChunk;
import com.rag.assistant.module.rag.service.DocumentParserService;
import com.rag.assistant.module.rag.service.EmbeddingService;
import com.rag.assistant.module.rag.service.QdrantSearchService;
import com.rag.assistant.module.rag.service.TextSplitterService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 用真实文档走完整链路（解析 -> 分块 -> 向量化 -> 入库 -> 检索）。
 * 文档默认取自测试资源 rag-test.md，可用 -Drag.test.document=<path> 指定外部文件。
 */
class IngestionPipelineVerifyTest {

    private static final String COLLECTION = "rag_ingest_verify";
    private static final double MIN_SCORE = 0.5;
    private static final String[] QUERIES = {
            "RAG 的核心流程是什么？",
            "RAG 有哪些优势？",
            "这种知识库问答适合什么场景？"
    };

    @Test
    void ingestDocumentAndRetrieve() throws Exception {
        assumeTrue(reachable(), "Qdrant 未启动，跳过验证");

        DocumentParserService parser = new DocumentParserService();
        TextSplitterService splitter = new TextSplitterService(500, 50);
        String text;
        try (InputStream in = openFixture()) {
            text = parser.parseMd(in);
        }
        assumeTrue(text != null && !text.isBlank(), "验证文档为空，跳过");
        List<TextSegment> segments = splitter.split(text);
        System.out.printf("%n文档长度=%d 字符，分块数=%d，分块长度=%s%n", text.length(), segments.size(),
                segments.stream().map(s -> String.valueOf(s.text().length())).toList());

        QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder("localhost", 6334, false).build());
        try {
            BgeSmallZhV15EmbeddingModel model = new BgeSmallZhV15EmbeddingModel();
            if (client.listCollectionsAsync().get().contains(COLLECTION)) {
                client.deleteCollectionAsync(COLLECTION).get();
            }
            client.createCollectionAsync(COLLECTION, Collections.VectorParams.newBuilder()
                    .setSize(model.dimension()).setDistance(Collections.Distance.Cosine).build()).get();

            EmbeddingStore<TextSegment> store = QdrantEmbeddingStore.builder()
                    .client(client).collectionName(COLLECTION).build();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                segments.get(i).metadata().put("docId", "1");
                segments.get(i).metadata().put("docName", "rag-test.md");
                ids.add(UUID.randomUUID().toString());
            }
            store.addAll(ids, model.embedAll(segments).content(), segments);
            System.out.println("已写入向量点数 = " + client.countAsync(COLLECTION).get());

            QdrantConfig config = new QdrantConfig();
            config.setCollection(COLLECTION);
            QdrantSearchService service = new QdrantSearchService(
                    providerOf(client), config, new EmbeddingService(model), 5, MIN_SCORE);

            QdrantSearchService probe = new QdrantSearchService(
                    providerOf(client), config, new EmbeddingService(model), 5, 0.0);
            int hits = 0;
            for (String query : QUERIES) {
                List<RetrievedChunk> all = probe.search(query);
                List<RetrievedChunk> chunks = service.search(query);
                System.out.printf("查询：%s 实际相似度=%s -> 阈值 %.2f 下命中 %d 条%n", query,
                        all.stream().map(c -> String.format("%.4f", c.score())).toList(), MIN_SCORE, chunks.size());
                if (!chunks.isEmpty()) {
                    hits++;
                }
            }
            System.out.printf("召回率 = %d / %d%n", hits, QUERIES.length);
            assertTrue(hits >= 2, "召回率过低：" + hits + "/" + QUERIES.length);
        } finally {
            try {
                client.deleteCollectionAsync(COLLECTION).get();
            } catch (Exception ignored) {
            }
            client.close();
        }
    }

    private static InputStream openFixture() throws IOException {
        String override = System.getProperty("rag.test.document");
        if (override != null && !override.isBlank()) {
            return Files.newInputStream(Path.of(override));
        }
        InputStream fromClasspath = IngestionPipelineVerifyTest.class.getClassLoader().getResourceAsStream("rag-test.md");
        assertNotNull(fromClasspath, "测试资源 rag-test.md 缺失");
        return fromClasspath;
    }

    private static ObjectProvider<QdrantClient> providerOf(QdrantClient client) {
        return new ObjectProvider<>() {
            @Override
            public QdrantClient getObject() {
                return client;
            }
        };
    }

    private static boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6334), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}