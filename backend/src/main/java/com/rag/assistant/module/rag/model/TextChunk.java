package com.rag.assistant.module.rag.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("text_chunk")
public class TextChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Long kbId;

    private String content;

    private Integer chunkIndex;

    private Integer tokenCount;

    private String qdrantPointId;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
