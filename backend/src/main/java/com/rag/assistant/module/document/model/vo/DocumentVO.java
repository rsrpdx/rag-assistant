package com.rag.assistant.module.document.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {

    private Long id;
    private Long kbId;
    private String originalName;
    private Long fileSize;
    private String fileType;
    private Integer status;
    private Integer chunkCount;
    private String errorMsg;
    private LocalDateTime createdAt;
}
