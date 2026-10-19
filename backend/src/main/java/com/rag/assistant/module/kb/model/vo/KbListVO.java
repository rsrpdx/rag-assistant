package com.rag.assistant.module.kb.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbListVO {

    private Long id;
    private String name;
    private String description;
    private String category;
    private Integer status;
    private Integer documentCount;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
