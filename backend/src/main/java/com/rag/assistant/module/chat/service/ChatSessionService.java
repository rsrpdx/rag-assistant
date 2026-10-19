package com.rag.assistant.module.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.assistant.module.chat.model.entity.ChatSession;
import com.rag.assistant.module.chat.model.vo.SessionVO;

import java.util.List;

public interface ChatSessionService extends IService<ChatSession> {

    List<SessionVO> listByUserAndKb(Long userId, Long kbId);

    SessionVO create(Long userId, Long kbId, String title);

    void deleteSession(Long id, Long userId);
}
