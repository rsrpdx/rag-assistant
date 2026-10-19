package com.rag.assistant.module.rag.model.vo;

import lombok.Data;

@Data
public class TextChunkVO {
    private String content;
    private Integer chunkIndex;
}
