package com.rag.assistant.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.assistant.module.chat.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} AND is_deleted = 0 ORDER BY created_at ASC")
    List<ChatMessage> selectBySessionId(Long sessionId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM chat_message WHERE session_id = #{sessionId}")
    int deleteBySessionId(Long sessionId);

}