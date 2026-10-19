package com.rag.assistant.module.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.assistant.module.chat.mapper.ChatSessionMapper;
import com.rag.assistant.module.chat.model.entity.ChatSession;
import com.rag.assistant.module.dashboard.model.vo.DashboardStatsVO;
import com.rag.assistant.module.dashboard.service.DashboardService;
import com.rag.assistant.module.document.mapper.DocumentMapper;
import com.rag.assistant.module.document.model.entity.Document;
import com.rag.assistant.module.kb.mapper.KnowledgeBaseMapper;
import com.rag.assistant.module.kb.model.entity.KnowledgeBase;
import com.rag.assistant.module.rag.mapper.TextChunkMapper;
import com.rag.assistant.module.rag.model.TextChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final TextChunkMapper textChunkMapper;

    @Override
    public DashboardStatsVO getStats(Long userId) {
        long kbCount = knowledgeBaseMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getUserId, userId));
        long docCount = documentMapper.selectCount(
            new LambdaQueryWrapper<Document>().eq(Document::getUserId, userId));
        long chatCount = chatSessionMapper.selectCount(
            new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getUserId, userId));
        long chunkCount = documentMapper.selectList(
            new LambdaQueryWrapper<Document>().eq(Document::getUserId, userId))
            .stream().mapToLong(Document::getChunkCount).sum();
        return new DashboardStatsVO(kbCount, docCount, chatCount, chunkCount);
    }

    @Override
    public List<Map<String, Object>> getRecentActivity(Long userId) {
        List<Map<String, Object>> activities = new ArrayList<>();

        List<ChatSession> sessions = chatSessionMapper.selectList(
            new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdatedAt)
                .last("LIMIT 8"));
        for (ChatSession s : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("type", "chat");
            item.put("title", s.getTitle() != null ? s.getTitle() : "\u65b0\u5bf9\u8bdd");
            item.put("description", s.getMessageCount() + " \u6761\u6d88\u606f");
            item.put("time", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : "");
            activities.add(item);
        }

                // Knowledge base creations
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .orderByDesc(KnowledgeBase::getCreatedAt)
                .last("LIMIT 8"));
        for (KnowledgeBase kb : kbs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", kb.getId());
            item.put("type", "kb");
            item.put("title", kb.getName());
            item.put("description", kb.getDescription() != null ? kb.getDescription() : "");
            item.put("time", kb.getCreatedAt() != null ? kb.getCreatedAt().toString() : "");
            activities.add(item);
        }
        List<Document> docs = documentMapper.selectList(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId)
                .orderByDesc(Document::getCreatedAt)
                .last("LIMIT 8"));
        for (Document d : docs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", d.getId());
            item.put("type", "doc");
            item.put("title", d.getOriginalName());
            item.put("description", d.getChunkCount() + " \u4e2a\u5207\u7247");
            item.put("time", d.getCreatedAt().toString());
            item.put("kbId", d.getKbId());
            activities.add(item);
        }

        activities.sort((a, b) -> ((String) b.get("time")).compareTo((String) a.get("time")));
        return activities;
    }

    @Override
    public List<Map<String, Object>> search(Long userId, String keyword) {
        List<Map<String, Object>> results = new ArrayList<>();

        // Search in document content (chunks)
        List<Document> allUserDocs = documentMapper.selectList(
            new LambdaQueryWrapper<Document>().eq(Document::getUserId, userId));
        if (allUserDocs != null && !allUserDocs.isEmpty()) {
            List<Long> userDocIds = allUserDocs.stream().map(Document::getId).toList();
            List<TextChunk> chunks = textChunkMapper.selectList(
                new LambdaQueryWrapper<TextChunk>()
                    .in(TextChunk::getDocId, userDocIds)
                    .like(TextChunk::getContent, keyword)
                    .last("LIMIT 5"));
            for (TextChunk chunk : chunks) {
                Document doc = allUserDocs.stream()
                    .filter(d -> d.getId().equals(chunk.getDocId()))
                    .findFirst().orElse(null);
                Map<String, Object> item = new HashMap<>();
                item.put("id", chunk.getId());
                item.put("type", "chunk");
                item.put("title", doc != null ? doc.getOriginalName() : "\u6587\u6863");
                item.put("docName", doc != null ? doc.getOriginalName() : "\u6587\u6863");
                item.put("content", truncate(chunk.getContent(), 150));
                item.put("description", truncate(chunk.getContent(), 100));
                item.put("url", doc != null ? "/doc/" + doc.getId() : "/documents");
                item.put("time", doc != null && doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "");
                results.add(item);
            }
        }

        // Search knowledge bases
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .like(KnowledgeBase::getName, keyword)
                .last("LIMIT 5"));
        for (KnowledgeBase kb : kbs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", kb.getId());
            item.put("type", "kb");
            item.put("title", kb.getName());
            item.put("description", kb.getDescription() != null ? kb.getDescription() : "");
            item.put("url", "/kb/" + kb.getId());
            item.put("time", kb.getCreatedAt() != null ? kb.getCreatedAt().toString() : "");
            results.add(item);
        }

        // Search documents
        List<Document> docs = documentMapper.selectList(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId)
                .like(Document::getOriginalName, keyword)
                .last("LIMIT 5"));
        for (Document d : docs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", d.getId());
            item.put("type", "doc");
            item.put("title", d.getOriginalName());
            item.put("description", d.getFileType() + " \u00b7 " + d.getChunkCount() + " \u4e2a\u5207\u7247");
            item.put("url", "/kb/" + d.getKbId());
            item.put("time", d.getCreatedAt().toString());
            results.add(item);
        }

        // Search chat sessions
        List<ChatSession> sessions = chatSessionMapper.selectList(
            new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .like(ChatSession::getTitle, keyword)
                .last("LIMIT 5"));
        for (ChatSession s : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("type", "chat");
            item.put("title", s.getTitle() != null ? s.getTitle() : "\u65b0\u5bf9\u8bdd");
            item.put("description", s.getMessageCount() + " \u6761\u6d88\u606f");
            item.put("url", "/chat?sessionId=" + s.getId());
            item.put("time", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : "");
            results.add(item);
        }

        // Sort by time descending
        results.sort((a, b) -> {
            String ta = (String) a.getOrDefault("time", "");
            String tb = (String) b.getOrDefault("time", "");
            return tb.compareTo(ta);
        });
        return results;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
