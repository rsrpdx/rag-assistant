package com.rag.assistant.module.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.assistant.module.rag.model.TextChunk;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TextChunkMapper extends BaseMapper<TextChunk> {

    @Select("SELECT * FROM text_chunk WHERE doc_id = #{docId} AND is_deleted = 0 ORDER BY chunk_index ASC")
    List<TextChunk> selectByDocId(Long docId);

    @Select("SELECT COUNT(*) FROM text_chunk WHERE kb_id = #{kbId} AND is_deleted = 0")
    long countByKbId(Long kbId);

    @Select("SELECT qdrant_point_id FROM text_chunk WHERE doc_id = #{docId} AND is_deleted = 0 AND qdrant_point_id IS NOT NULL")
    java.util.List<String> selectQdrantPointIdsByDocId(Long docId);

    @Select("SELECT qdrant_point_id FROM text_chunk WHERE kb_id = #{kbId} AND is_deleted = 0 AND qdrant_point_id IS NOT NULL")
    java.util.List<String> selectQdrantPointIdsByKbId(Long kbId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM text_chunk WHERE doc_id = #{docId}")
    int deleteByDocId(Long docId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM text_chunk WHERE kb_id = #{kbId}")
    int deleteByKbId(Long kbId);

}