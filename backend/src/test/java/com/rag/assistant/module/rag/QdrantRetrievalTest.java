package com.rag.assistant.module.rag;

import com.rag.assistant.module.rag.config.QdrantConfig;
import com.rag.assistant.module.rag.model.RetrievedChunk;
import com.rag.assistant.module.rag.service.EmbeddingService;
import com.rag.assistant.module.rag.service.QdrantSearchService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 端到端检索验证：按生产路径写入 Qdrant（langchain4j EmbeddingStore），
 * 再用 QdrantSearchService 检索，确认改写提问能召回正确片段。
 * Qdrant 未启动时跳过；启动后的任何异常都会作为测试失败暴露。
 */
class QdrantRetrievalTest {

    private static final String HOST = "localhost";
    private static final int GRPC_PORT = 6334;
    private static final String COLLECTION = "rag_eval_test";
    private static final double MIN_SCORE = 0.5;
    private static final int MAX_RESULTS = 5;

    private static final String[] DOCS = {
            "忘记密码时，可在登录页点击找回密码，系统会向注册邮箱发送重置链接，链接有效期为 30 分钟。",
            "员工差旅报销需在出差结束后 15 个工作日内提交发票与审批单，超期需部门负责人签字说明。",
            "知识库支持上传 PDF 与 Word 文档，单个文件不超过 50MB，解析完成后自动进行分块与向量化。"
    };

    private static final String[] QUERIES = {
            "密码忘了怎么办？",
            "出差费用什么时候提交？",
            "上传文档有大小限制吗？"
    };

    @Test
    void shouldRetrieveMatchingChunkThroughSearchService() throws Exception {
        assumeTrue(isQdrantReachable(), "Qdrant 未启动，跳过端到端检索验证");

        QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder(HOST, GRPC_PORT, false).build());
        try {
            EmbeddingModel model = new BgeSmallZhV15EmbeddingModel();
            if (client.listCollectionsAsync().get().contains(COLLECTION)) {
                client.deleteCollectionAsync(COLLECTION).get();
            }
            client.createCollectionAsync(COLLECTION, Collections.VectorParams.newBuilder()
                    .setSize(model.dimension())
                    .setDistance(Collections.Distance.Cosine)
                    .build()).get();

            EmbeddingStore<TextSegment> store = QdrantEmbeddingStore.builder()
                    .client(client)
                    .collectionName(COLLECTION)
                    .build();

            List<TextSegment> segments = new ArrayList<>();
            List<String> pointIds = new ArrayList<>();
            for (int i = 0; i < DOCS.length; i++) {
                TextSegment segment = TextSegment.from(DOCS[i]);
                segment.metadata().put("docId", String.valueOf(i + 1));
                segment.metadata().put("docName", "doc-" + (i + 1) + ".md");
                segments.add(segment);
                pointIds.add(UUID.randomUUID().toString());
            }
            store.addAll(pointIds, model.embedAll(segments).content(), segments);

            QdrantConfig config = new QdrantConfig();
            config.setCollection(COLLECTION);
            QdrantSearchService searchService = new QdrantSearchService(
                    providerOf(client), config, new EmbeddingService(model), MAX_RESULTS, MIN_SCORE);

            System.out.println();
            int correctTop1 = 0;
            for (int i = 0; i < QUERIES.length; i++) {
                List<RetrievedChunk> chunks = searchService.search(QUERIES[i]);
                System.out.printf("查询：%s -> 命中 %d 条%n", QUERIES[i], chunks.size());
                for (RetrievedChunk chunk : chunks) {
                    System.out.printf("  score=%.4f docName=%s text=%s%n",
                            chunk.score(), chunk.metadata().get("docName"), chunk.text());
                }
                assertFalse(chunks.isEmpty(), "未召回任何片段：" + QUERIES[i]);
                if (DOCS[i].equals(chunks.get(0).text())) {
                    correctTop1++;
                }
            }
            System.out.printf("Top1 命中正确文档：%d / %d%n", correctTop1, QUERIES.length);
            System.out.println();

            assertEquals(QUERIES.length, correctTop1, "存在未召回正确文档的提问");
            assertTrue(searchService.search(QUERIES[0]).get(0).score() >= MIN_SCORE, "分数低于阈值");
            assertEquals("doc-1.md", searchService.search(QUERIES[0]).get(0).metadata().get("docName"));
        } finally {
            try {
                client.deleteCollectionAsync(COLLECTION).get();
            } catch (Exception ignored) {
                // 清理失败不影响验证结论
            }
            client.close();
        }
    }

    private static ObjectProvider<QdrantClient> providerOf(QdrantClient client) {
        return new ObjectProvider<>() {
            @Override
            public QdrantClient getObject() {
                return client;
            }
        };
    }

    private static boolean isQdrantReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, GRPC_PORT), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}