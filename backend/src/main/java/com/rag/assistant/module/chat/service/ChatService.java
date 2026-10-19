package com.rag.assistant.module.chat.service;

import com.rag.assistant.module.chat.model.vo.MessageVO;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    MessageVO ask(Long sessionId, String question, Long userId);

    SseEmitter askStream(Long sessionId, String question, Long userId);

    List<MessageVO> getMessages(Long sessionId, Long userId, Integer offset, Integer limit);
}
