package com.rag.assistant.module.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.assistant.common.enums.ResultCodeEnum;
import com.rag.assistant.common.exception.BusinessException;
import com.rag.assistant.module.kb.mapper.KnowledgeBaseMapper;
import com.rag.assistant.module.document.mapper.DocumentMapper;
import com.rag.assistant.module.document.model.entity.Document;
import com.rag.assistant.module.rag.mapper.TextChunkMapper;
import com.rag.assistant.module.rag.service.VectorStoreService;
import com.rag.assistant.module.chat.mapper.ChatSessionMapper;
import com.rag.assistant.module.chat.mapper.ChatMessageMapper;
import com.rag.assistant.module.chat.model.entity.ChatSession;
import com.rag.assistant.thirdparty.minio.MinioService;
import com.rag.assistant.module.kb.model.dto.CreateKbRequest;
import com.rag.assistant.module.kb.model.dto.UpdateKbRequest;
import com.rag.assistant.module.kb.model.entity.KnowledgeBase;
import com.rag.assistant.module.kb.model.vo.KbDetailVO;
import com.rag.assistant.module.kb.model.vo.CategoryVO;
import com.rag.assistant.module.kb.model.vo.KbListVO;
import com.rag.assistant.module.kb.model.vo.KbStatsVO;
import com.rag.assistant.module.kb.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final TextChunkMapper textChunkMapper;
    private final VectorStoreService vectorStoreService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final MinioService minioService;

    @Override
    public IPage<KbListVO> pageByUser(Long userId, Integer page, Integer size, String category) {
        Page<KnowledgeBase> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId);
        if (category != null && !category.isEmpty()) {
            wrapper.eq(KnowledgeBase::getCategory, category);
        }
        wrapper.orderByDesc(KnowledgeBase::getUpdatedAt);

        IPage<KnowledgeBase> kbPage = knowledgeBaseMapper.selectPage(pageParam, wrapper);

        Page<KbListVO> voPage = new Page<>(kbPage.getCurrent(), kbPage.getSize(), kbPage.getTotal());
        voPage.setRecords(kbPage.getRecords().stream().map(this::toListVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public KbDetailVO create(Long userId, CreateKbRequest request) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setCategory(request.getCategory());
        kb.setUserId(userId);
        kb.setStatus(0);
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        kb.setCreatedAt(LocalDateTime.now());
        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.insert(kb);
        return toDetailVO(kb);
    }

    @Override
    public KbDetailVO getDetail(Long id, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的知识库");
        }
        return toDetailVO(kb);
    }

    @Override
    public void update(Long id, UpdateKbRequest request, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的知识库");
        }
        if (request.getName() != null) kb.setName(request.getName());
        if (request.getDescription() != null) kb.setDescription(request.getDescription());
        if (request.getCategory() != null) kb.setCategory(request.getCategory());
        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的知识库");
        }

        int totalChunksDeleted = 0;
        int totalDocsDeleted = 0;
        int totalSessionsDeleted = 0;
        int totalMessagesDeleted = 0;

        // 1. Delete all vectors + chunks + files for every document in this KB
        java.util.List<Document> docs = documentMapper.selectByKbId(id);
        if (docs != null) {
            for (Document doc : docs) {
                // Delete vectors from embedding store
                java.util.List<String> pointIds = textChunkMapper.selectQdrantPointIdsByDocId(doc.getId());
                if (pointIds != null && !pointIds.isEmpty()) {
                    vectorStoreService.remove(pointIds);
                }
                // Delete text chunks
                int cd = textChunkMapper.deleteByDocId(doc.getId());
                totalChunksDeleted += cd;
                // Delete MinIO file
                minioService.delete(doc.getFilename());
                totalDocsDeleted++;
            }
        }

        // 2. Delete all documents in this KB
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document> docWrapper =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getKbId, id);
        documentMapper.delete(docWrapper);

        // 3. Delete all chat messages + sessions for this KB
        java.util.List<ChatSession> sessions = chatSessionMapper.selectByKbId(id);
        if (sessions != null) {
            for (ChatSession session : sessions) {
                int md = chatMessageMapper.deleteBySessionId(session.getId());
                totalMessagesDeleted += md;
                totalSessionsDeleted++;
            }
        }

        // 4. Delete all chat sessions
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession> sessionWrapper =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getKbId, id);
        chatSessionMapper.delete(sessionWrapper);

        // 5. Delete KB itself
        knowledgeBaseMapper.deleteById(id);

        if (log.isDebugEnabled()) {
            log.debug("KB cascade deleted: id=" + id + ", name=" + kb.getName()
                + ", docsDeleted=" + totalDocsDeleted + ", chunksDeleted=" + totalChunksDeleted
                + ", sessionsDeleted=" + totalSessionsDeleted + ", messagesDeleted=" + totalMessagesDeleted);
        }
    }

    @Override
    public KbStatsVO getStats(Long id, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的知识库");
        }
        KbStatsVO vo = new KbStatsVO();
        vo.setDocumentCount(kb.getDocumentCount());
        vo.setChunkCount(kb.getChunkCount());
        return vo;
    }

    @Override
    public java.util.List<CategoryVO> getCategories(Long userId) {
        java.util.Set<String> cats = new java.util.LinkedHashSet<>();
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .select(KnowledgeBase::getCategory);
        java.util.List<KnowledgeBase> list = knowledgeBaseMapper.selectList(wrapper);
        for (KnowledgeBase kb : list) {
            if (kb.getCategory() != null && !kb.getCategory().isEmpty()) {
                cats.add(kb.getCategory());
            }
        }
        return cats.stream().map(c -> {
            CategoryVO vo = new CategoryVO();
            vo.setName(c);
            return vo;
        }).collect(Collectors.toList());
    }

    private KbListVO toListVO(KnowledgeBase kb) {
        KbListVO vo = new KbListVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setCategory(kb.getCategory());
        vo.setStatus(kb.getStatus());
        vo.setDocumentCount(kb.getDocumentCount());
        vo.setChunkCount(kb.getChunkCount());
        vo.setCreatedAt(kb.getCreatedAt());
        vo.setUpdatedAt(kb.getUpdatedAt());
        return vo;
    }

    private KbDetailVO toDetailVO(KnowledgeBase kb) {
        KbDetailVO vo = new KbDetailVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setCategory(kb.getCategory());
        vo.setUserId(kb.getUserId());
        vo.setStatus(kb.getStatus());
        vo.setDocumentCount(kb.getDocumentCount());
        vo.setChunkCount(kb.getChunkCount());
        vo.setCreatedAt(kb.getCreatedAt());
        vo.setUpdatedAt(kb.getUpdatedAt());
        return vo;
    }
}
