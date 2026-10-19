package com.rag.assistant.module.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.assistant.common.enums.ResultCodeEnum;
import com.rag.assistant.common.exception.BusinessException;
import com.rag.assistant.module.chat.mapper.ChatSessionMapper;
import com.rag.assistant.module.chat.model.entity.ChatSession;
import com.rag.assistant.module.chat.model.vo.SessionVO;
import com.rag.assistant.module.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public List<SessionVO> listByUserAndKb(Long userId, Long kbId) {
        return chatSessionMapper.selectByUserIdAndKbId(userId, kbId)
                .stream().map(this::toVO).toList();
    }

    @Override
    public SessionVO create(Long userId, Long kbId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setKbId(kbId);
        session.setTitle(title);
        session.setMessageCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return toVO(session);
    }

    @Override
    public void deleteSession(Long id, Long userId) {
        ChatSession session = chatSessionMapper.selectById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "会话不存在");
        }
        chatSessionMapper.deleteById(id);
    }

    private SessionVO toVO(ChatSession s) {
        SessionVO vo = new SessionVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setKbId(s.getKbId());
        vo.setMessageCount(s.getMessageCount());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }
}
