package com.rag.assistant.module.rag.service;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分块策略：500 字符窗口 + 50 字符重叠（对应 application.yml 的 rag.doc.chunk）。
 * 这是检索链路的第一环，分块长度与重叠直接决定向量粒度与召回质量。
 */
class TextSplitterServiceTest {

    private static final int MAX_SIZE = 500;
    private static final int OVERLAP = 50;

    private final TextSplitterService splitter = new TextSplitterService(MAX_SIZE, OVERLAP);

    @Test
    @DisplayName("短文本只产生一个分块，内容完整保留")
    void shortTextStaysInOneChunk() {
        String text = "知识库支持上传 PDF 与 Word 文档，解析完成后自动分块并向量化。";

        List<TextSegment> chunks = splitter.split(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).text());
    }

    @Test
    @DisplayName("长文本每个分块都不超过窗口大小")
    void longTextIsSplitWithinLimit() {
        String text = "abcdefghijklmnopqrstuvwxyz".repeat(50);

        List<TextSegment> chunks = splitter.split(text);

        assertTrue(chunks.size() >= 3, "分块数过少：" + chunks.size());
        for (TextSegment chunk : chunks) {
            assertTrue(chunk.text().length() <= MAX_SIZE, "分块长度超限：" + chunk.text().length());
        }
    }

    @Test
    @DisplayName("相邻分块保留 50 字符重叠，答案在切点处不会被截断")
    void neighbourChunksOverlap() {
        String text = "abcdefghijklmnopqrstuvwxyz".repeat(50);

        List<TextSegment> chunks = splitter.split(text);
        String first = chunks.get(0).text();
        String second = chunks.get(1).text();

        assertEquals(text.substring(MAX_SIZE - OVERLAP, MAX_SIZE), second.substring(0, OVERLAP));
        assertTrue(first.endsWith(second.substring(0, OVERLAP)), "首块尾部与次块头部没有重叠");
    }

    @Test
    @DisplayName("滑动窗口不丢内容：去掉重叠前缀即可还原原文")
    void slidingWindowCoversWholeText() {
        String text = "检索增强生成把文档切片后写入向量库。".repeat(60);

        List<TextSegment> chunks = splitter.split(text);
        StringBuilder restored = new StringBuilder(chunks.get(0).text());
        for (int i = 1; i < chunks.size(); i++) {
            restored.append(chunks.get(i).text(), OVERLAP, chunks.get(i).text().length());
        }

        assertEquals(text, restored.toString());
        assertTrue(chunks.get(chunks.size() - 1).text().endsWith(text.substring(text.length() - 20)));
    }

    @Test
    @DisplayName("空白输入不产生分块，窗口可自定义")
    void blankInputAndCustomWindow() {
        assertTrue(splitter.split(null).isEmpty());
        assertTrue(splitter.split("   \n  ").isEmpty());

        List<TextSegment> chunks = new TextSplitterService(80, 10).split("字".repeat(200));

        assertEquals(80, chunks.get(0).text().length());
        assertEquals("字".repeat(10), chunks.get(1).text().substring(0, 10));
    }
}