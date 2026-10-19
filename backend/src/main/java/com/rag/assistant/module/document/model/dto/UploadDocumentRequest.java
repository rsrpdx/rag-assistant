package com.rag.assistant.module.document.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadDocumentRequest {

    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    @NotNull(message = "文件不能为空")
    private MultipartFile file;
}
