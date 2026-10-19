package com.rag.assistant.module.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对照测试：旧（字符 n-gram 哈希）与新（bge-small-zh-v1.5）两套向量化方案
 * 在中文改写提问上的召回表现，用于确认检索链路的修复效果。
 */
class EmbeddingQualityTest {

    private static final double[] THRESHOLDS = {0.4, 0.6};

    private static final String[] DOCS = {
            "忘记密码时，可在登录页点击找回密码，系统会向注册邮箱发送重置链接，链接有效期为 30 分钟。",
            "员工差旅报销需在出差结束后 15 个工作日内提交发票与审批单，超期需部门负责人签字说明。",
            "知识库支持上传 PDF 与 Word 文档，单个文件不超过 50MB，解析完成后自动进行分块与向量化。"
    };

    private static final String[] QUERIES = {
            "密码忘了怎么办？",
            "出差费用什么时候提交？",
            "上传文档有大小限制吗？",
            "今天中午吃什么？"
    };

    @Test
    void compareHashBaselineWithBgeSmallZh() {
        EmbeddingModel hash = new HashEmbeddingModelFixture();
        EmbeddingModel bge = new BgeSmallZhV15EmbeddingModel();

        float[][] docHash = new float[DOCS.length][];
        float[][] docBge = new float[DOCS.length][];
        for (int i = 0; i < DOCS.length; i++) {
            docHash[i] = hash.embed(DOCS[i]).content().vector();
            docBge[i] = bge.embed(DOCS[i]).content().vector();
        }

        int[] hashHits = new int[THRESHOLDS.length];
        int[] bgeHits = new int[THRESHOLDS.length];
        int[] bgeTop1Correct = {0};

        System.out.println();
        System.out.println("查询 | 文档 | 哈希相似度 | 语义相似度");
        for (int q = 0; q < QUERIES.length; q++) {
            float[] qHash = hash.embed(QUERIES[q]).content().vector();
            float[] qBge = bge.embed(QUERIES[q]).content().vector();
            int bestDoc = -1;
            double bestScore = -1;
            for (int d = 0; d < DOCS.length; d++) {
                double h = cosine(qHash, docHash[d]);
                double b = cosine(qBge, docBge[d]);
                System.out.printf("%s | 文档%d | %.4f | %.4f%n", QUERIES[q], d + 1, h, b);
                for (int t = 0; t < THRESHOLDS.length; t++) {
                    if (h >= THRESHOLDS[t]) hashHits[t]++;
                    if (b >= THRESHOLDS[t]) bgeHits[t]++;
                }
                if (b > bestScore) {
                    bestScore = b;
                    bestDoc = d;
                }
            }
            if (q < DOCS.length && bestDoc == q) {
                bgeTop1Correct[0]++;
            }
        }

        System.out.println();
        for (int t = 0; t < THRESHOLDS.length; t++) {
            System.out.printf("阈值 %.2f：哈希命中 %d 次，语义模型命中 %d 次%n",
                    THRESHOLDS[t], hashHits[t], bgeHits[t]);
        }
        System.out.printf("语义模型 Top1 命中正确文档：%d / 3%n", bgeTop1Correct[0]);
        System.out.println();

        assertTrue(bgeTop1Correct[0] == 3, "语义模型未能对全部改写提问召回正确文档");
        assertTrue(bgeHits[0] >= 3, "语义模型在阈值 0.4 下命中数不足");
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}