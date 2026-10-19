package com.rag.assistant.module.rag.service;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块：固定窗口 + 相邻窗口重叠的滑动切分。
 *
 * <p>langchain4j 自带的 {@code DocumentByCharacterSplitter} / {@code DocumentSplitters.recursive}
 * 在当前版本中并不会真正使用 overlap 参数（实测相邻分块重叠恒为 0），
 * 因此这里直接实现滑动窗口，保证配置里的 overlap 生效。
 *
 * @see <a href="https://docs.langchain4j.dev/tutorials/rag">LangChain4j RAG</a>
 */
@Slf4j
@Service
public class TextSplitterService {

    private static final int DEFAULT_MAX_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    private final int maxChunkSize;
    private final int overlap;

    public TextSplitterService(
            @Value("${rag.doc.chunk.max-size:" + DEFAULT_MAX_SIZE + "}") int maxChunkSize,
            @Value("${rag.doc.chunk.overlap:" + DEFAULT_OVERLAP + "}") int overlap) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("块大小必须为正数，当前值：" + maxChunkSize);
        }
        this.maxChunkSize = maxChunkSize;
        this.overlap = Math.max(0, Math.min(overlap, maxChunkSize - 1));
    }

    public List<TextSegment> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int step = maxChunkSize - overlap;
        List<TextSegment> segments = new ArrayList<>();
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + maxChunkSize);
            segments.add(TextSegment.from(text.substring(start, end)));
            if (end == text.length()) {
                break;
            }
        }

        log.debug("Split text into {} chunks (size={}, overlap={})", segments.size(), maxChunkSize, overlap);
        return segments;
    }
}