package com.rag.assistant.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.assistant.module.document.model.entity.Document;
import org.apache.ibatis.annotations.Select;

public interface DocumentMapper extends BaseMapper<Document> {

    @Select("SELECT COUNT(*) FROM document WHERE kb_id = #{kbId} AND is_deleted = 0")
    long countByKbId(Long kbId);

    @Select("SELECT * FROM document WHERE kb_id = #{kbId} AND is_deleted = 0 ORDER BY created_at DESC")
    java.util.List<com.rag.assistant.module.document.model.entity.Document> selectByKbId(Long kbId);

}