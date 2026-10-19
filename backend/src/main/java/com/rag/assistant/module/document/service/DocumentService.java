package com.rag.assistant.module.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.assistant.module.document.model.entity.Document;
import com.rag.assistant.module.document.model.vo.DocumentDetailVO;
import com.rag.assistant.module.document.model.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService extends IService<Document> {

    DocumentVO upload(MultipartFile file, Long kbId, Long userId);

    IPage<DocumentVO> pageByKbId(Long kbId, Integer page, Integer size, Long userId);

    DocumentDetailVO getDetail(Long id, Long userId);

    void deleteDocument(Long id, Long userId);

    void retryParse(Long id, Long userId);

    IPage<DocumentVO> pageByUserId(Long userId, Integer page, Integer size);
}
