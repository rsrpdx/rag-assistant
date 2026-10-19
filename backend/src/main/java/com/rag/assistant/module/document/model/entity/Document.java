package com.rag.assistant.module.document.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private Long userId;

    private String filename;

    private String originalName;

    private Long fileSize;

    private String fileType;

    private String fileUrl;

    private Integer status;

    private Integer chunkCount;

    private String errorMsg;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
