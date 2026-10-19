package com.rag.assistant.module.chat.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    private String title;
}
