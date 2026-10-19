package com.rag.assistant.module.user.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private String phone;
    private String role;
    private LocalDateTime createdAt;
}
