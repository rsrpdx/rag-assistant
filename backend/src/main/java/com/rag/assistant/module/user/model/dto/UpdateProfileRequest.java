package com.rag.assistant.module.user.model.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String nickname;
    private String email;
    private String phone;
}
