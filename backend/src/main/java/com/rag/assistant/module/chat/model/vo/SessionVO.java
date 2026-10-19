package com.rag.assistant.module.chat.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionVO {

    private Long id;
    private String title;
    private Long kbId;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
