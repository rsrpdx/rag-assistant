package com.rag.assistant.module.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.assistant.common.enums.ResultCodeEnum;
import com.rag.assistant.common.exception.BusinessException;
import com.rag.assistant.module.document.mapper.DocumentMapper;
import com.rag.assistant.module.rag.mapper.TextChunkMapper;
import com.rag.assistant.module.rag.model.TextChunk;
import com.rag.assistant.module.rag.model.vo.TextChunkVO;
import com.rag.assistant.module.rag.service.VectorStoreService;
import com.rag.assistant.module.document.model.entity.Document;
import com.rag.assistant.module.document.model.vo.DocumentDetailVO;
import com.rag.assistant.module.document.model.vo.DocumentVO;
import com.rag.assistant.module.document.service.DocumentService;
import com.rag.assistant.module.kb.model.entity.KnowledgeBase;
import com.rag.assistant.module.rag.service.RagService;
import com.rag.assistant.thirdparty.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private final DocumentMapper documentMapper;
    private final MinioService minioService;
    private final com.rag.assistant.module.kb.mapper.KnowledgeBaseMapper knowledgeBaseMapper;
    private final RagService ragService;
    private final TextChunkMapper textChunkMapper;
    private final VectorStoreService vectorStoreService;

    @Override
    public DocumentVO upload(MultipartFile file, Long kbId, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的知识库");
        }

        String originalName = file.getOriginalFilename();
        log.info("Upload file: originalName={}, size={}", originalName, file.getSize());
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "文件名不能为空");
        }

        String fileType = getFileType(originalName);
        if (!isSupportedType(fileType)) {
            throw new BusinessException(ResultCodeEnum.UNSUPPORTED_FILE_TYPE, "仅支持PDF、MD、DOC 格式文件");
        }

        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException(ResultCodeEnum.FILE_TOO_LARGE, "文件大小不能超过50MB");
        }

        String objectName = minioService.upload(file, originalName);
        String fileUrl = minioService.getFileUrl(objectName);

        Document document = new Document();
        document.setKbId(kbId);
        document.setUserId(userId);
        document.setFilename(objectName);
        document.setOriginalName(originalName);
        document.setFileSize(file.getSize());
        document.setFileType(fileType);
        document.setFileUrl(fileUrl);
        document.setStatus(0);
        document.setChunkCount(0);
        document.setIsDeleted(0);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);

        kb.setDocumentCount(kb.getDocumentCount() + 1);
        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);

        java.util.concurrent.CompletableFuture.runAsync(() -> ragService.processDocument(document.getId()));

        return toVO(document);
    }

    @Override
    public IPage<DocumentVO> pageByKbId(Long kbId, Integer page, Integer size, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的知识库");
        }

        Page<Document> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getKbId, kbId)
                .orderByDesc(Document::getCreatedAt);

        IPage<Document> documentPage = documentMapper.selectPage(pageParam, wrapper);

        Page<DocumentVO> voPage = new Page<>(documentPage.getCurrent(), documentPage.getSize(), documentPage.getTotal());
        voPage.setRecords(documentPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public DocumentDetailVO getDetail(Long id, Long userId) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "文档不存在");
        }
        if (!document.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的文档");
        }
        return toDetailVO(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id, Long userId) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "文档不存在");
        }
        if (!document.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的文档");
        }

        // 1. Delete vectors from embedding store
        java.util.List<String> pointIds = textChunkMapper.selectQdrantPointIdsByDocId(id);
        if (pointIds != null && !pointIds.isEmpty()) {
            vectorStoreService.remove(pointIds);
            log.info("Removed {} vectors for document id={}", pointIds.size(), id);
        }

        // 2. Delete text chunks from DB
        int chunkDeleted = textChunkMapper.deleteByDocId(id);
        log.info("Deleted {} text chunks for document id={}", chunkDeleted, id);

        // 3. Delete file from MinIO
        minioService.delete(document.getFilename());

        // 4. Delete document
        documentMapper.deleteById(id);

        // 5. Update KB counts
        KnowledgeBase kb = knowledgeBaseMapper.selectById(document.getKbId());
        if (kb != null) {
            kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
            kb.setChunkCount(Math.max(0, kb.getChunkCount() - chunkDeleted));
            kb.setUpdatedAt(LocalDateTime.now());
            knowledgeBaseMapper.updateById(kb);
        }

        log.info("Document deleted: id={}, originalName={}, userId={}, chunksDeleted={}", id, document.getOriginalName(), userId, chunkDeleted);
    }

    @Override
    public IPage<DocumentVO> pageByUserId(Long userId, Integer page, Integer size) {
        Page<Document> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId)
                .orderByDesc(Document::getCreatedAt);
        return documentMapper.selectPage(pageParam, wrapper).convert(this::toVO);
    }

    @Override
    public void retryParse(Long id, Long userId) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "文档不存在");
        }
        if (!document.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "只能操作自己的文档");
        }
        // Delete old chunks before re-processing to avoid duplicate key errors
        java.util.List<String> oldPointIds = textChunkMapper.selectQdrantPointIdsByDocId(id);
        if (oldPointIds != null && !oldPointIds.isEmpty()) {
            vectorStoreService.remove(oldPointIds);
        }
        textChunkMapper.deleteByDocId(id);

        document.setStatus(0);
        document.setErrorMsg(null);
        document.setChunkCount(0);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        java.util.concurrent.CompletableFuture.runAsync(() -> ragService.processDocument(id));
    }

    private String getFileType(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex < 0) return "";
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private boolean isSupportedType(String fileType) {
        return "pdf".equals(fileType) || "md".equals(fileType) || "doc".equals(fileType) || "docx".equals(fileType);
    }

    private DocumentVO toVO(Document document) {
        DocumentVO vo = new DocumentVO();
        vo.setId(document.getId());
        vo.setKbId(document.getKbId());
        vo.setOriginalName(document.getOriginalName());
        vo.setFileSize(document.getFileSize());
        vo.setFileType(document.getFileType());
        vo.setStatus(document.getStatus());
        vo.setChunkCount(document.getChunkCount());
        vo.setErrorMsg(document.getErrorMsg());
        vo.setCreatedAt(document.getCreatedAt());
        return vo;
    }

    private DocumentDetailVO toDetailVO(Document document) {
        DocumentDetailVO vo = new DocumentDetailVO();
        vo.setId(document.getId());
        vo.setKbId(document.getKbId());
        vo.setUserId(document.getUserId());
        vo.setFilename(document.getFilename());
        vo.setOriginalName(document.getOriginalName());
        vo.setFileSize(document.getFileSize());
        vo.setFileType(document.getFileType());
        vo.setFileUrl(minioService.getFileUrl(document.getFilename()));
        vo.setStatus(document.getStatus());
        vo.setChunkCount(document.getChunkCount());
        vo.setErrorMsg(document.getErrorMsg());
        vo.setCreatedAt(document.getCreatedAt());
        vo.setUpdatedAt(document.getUpdatedAt());

        // Query text chunks
        List<TextChunk> textChunks = textChunkMapper.selectByDocId(document.getId());
        if (textChunks != null && !textChunks.isEmpty()) {
            List<TextChunkVO> chunkVOs = textChunks.stream().map(tc -> {
                TextChunkVO cv = new TextChunkVO();
                cv.setContent(tc.getContent());
                cv.setChunkIndex(tc.getChunkIndex());
                return cv;
            }).collect(Collectors.toList());
            vo.setChunks(chunkVOs);
        }

        return vo;
    }
}
