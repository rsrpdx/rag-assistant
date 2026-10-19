package com.rag.assistant.module.document.model.vo;

import com.rag.assistant.module.rag.model.vo.TextChunkVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DocumentDetailVO {

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TextChunkVO> chunks;
}
