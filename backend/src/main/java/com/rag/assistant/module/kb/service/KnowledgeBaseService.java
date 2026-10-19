package com.rag.assistant.module.kb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.assistant.module.kb.model.dto.CreateKbRequest;
import com.rag.assistant.module.kb.model.dto.UpdateKbRequest;
import com.rag.assistant.module.kb.model.entity.KnowledgeBase;
import com.rag.assistant.module.kb.model.vo.KbDetailVO;
import com.rag.assistant.module.kb.model.vo.KbListVO;
import com.rag.assistant.module.kb.model.vo.CategoryVO;
import com.rag.assistant.module.kb.model.vo.KbStatsVO;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    IPage<KbListVO> pageByUser(Long userId, Integer page, Integer size, String category);

    KbDetailVO create(Long userId, CreateKbRequest request);

    KbDetailVO getDetail(Long id, Long userId);

    void update(Long id, UpdateKbRequest request, Long userId);

    void delete(Long id, Long userId);

    KbStatsVO getStats(Long id, Long userId);

    java.util.List<CategoryVO> getCategories(Long userId);
}
