package com.rag.assistant.module.rag.service;

import com.rag.assistant.module.document.mapper.DocumentMapper;
import com.rag.assistant.module.document.model.entity.Document;
import com.rag.assistant.module.kb.mapper.KnowledgeBaseMapper;
import com.rag.assistant.module.kb.model.entity.KnowledgeBase;
import com.rag.assistant.module.rag.mapper.TextChunkMapper;
import com.rag.assistant.module.rag.model.TextChunk;
import com.rag.assistant.thirdparty.minio.MinioService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final TextChunkMapper textChunkMapper;
    private final MinioService minioService;
    private final DocumentParserService documentParser;
    private final TextSplitterService textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;

            @Transactional(rollbackFor = Exception.class)
    public void processDocument(Long documentId) {
        log.info("Async processing started for document id={}", documentId);
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("Document not found: {}", documentId);
            return;
        }

        try {
            doc.setStatus(1);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);

            String text = documentParser.parseByType(minioService.download(doc.getFilename()), doc.getFileType());
            if (text == null || text.isBlank()) {
                throw new RuntimeException("文档内容为空，无法解析");
            }

            List<TextSegment> segments = textSplitter.split(text);
            for (TextSegment seg : segments) {
                seg.metadata().put("docId", String.valueOf(doc.getId()));
                seg.metadata().put("docName", doc.getOriginalName());
            }
            log.info("Document id={} parsed, {} chars -> {} chunks", documentId, text.length(), segments.size());

            for (int i = 0; i < segments.size(); i++) {
                TextChunk chunk = new TextChunk();
                chunk.setDocId(doc.getId());
                chunk.setKbId(doc.getKbId());
                chunk.setContent(segments.get(i).text());
                chunk.setChunkIndex(i);
                chunk.setTokenCount(segments.get(i).text().length() / 4);
                chunk.setCreatedAt(LocalDateTime.now());
                textChunkMapper.insert(chunk);
            }

            try {
                List<Embedding> embeddings = embeddingService.embedAll(segments);
                List<String> pointIds = vectorStore.store(segments, embeddings);
                for (int i = 0; i < segments.size() && i < pointIds.size(); i++) {
                    TextChunk chunk = textChunkMapper.selectByDocId(doc.getId()).get(i);
                    chunk.setQdrantPointId(pointIds.get(i));
                    textChunkMapper.updateById(chunk);
                }
                log.info("Vectors stored for document id={}", documentId);
            } catch (Exception e) {
                log.warn("Embedding/vector storage unavailable for document id={}: {}", documentId, e.getMessage());
            }

            doc.setStatus(2);
            doc.setChunkCount(segments.size());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);

            KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKbId());
            if (kb != null) {
                long totalChunks = textChunkMapper.countByKbId(doc.getKbId());
                kb.setChunkCount((int) totalChunks);
                kb.setStatus(2);
                kb.setUpdatedAt(LocalDateTime.now());
                knowledgeBaseMapper.updateById(kb);
            }

            log.info("Document processed: id={}, chunks={}", documentId, segments.size());
        } catch (Exception e) {
            log.error("Document processing failed: id={}", documentId, e);
            doc.setStatus(3);
            doc.setErrorMsg(e.getMessage());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
    }
}
