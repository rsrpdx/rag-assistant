package com.rag.assistant.module.chat.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AskRequest {

    @NotBlank(message = "问题不能为空")
    private String question;
}
