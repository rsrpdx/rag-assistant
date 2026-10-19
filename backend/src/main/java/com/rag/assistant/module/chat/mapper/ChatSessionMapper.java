package com.rag.assistant.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.assistant.module.chat.model.entity.ChatSession;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND kb_id = #{kbId} AND is_deleted = 0 ORDER BY updated_at DESC")
    List<ChatSession> selectByUserIdAndKbId(Long userId, Long kbId);

    @Select("SELECT * FROM chat_session WHERE kb_id = #{kbId} AND is_deleted = 0 ORDER BY updated_at DESC")
    java.util.List<com.rag.assistant.module.chat.model.entity.ChatSession> selectByKbId(Long kbId);

}