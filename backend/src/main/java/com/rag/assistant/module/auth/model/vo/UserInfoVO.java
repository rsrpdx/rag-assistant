package com.rag.assistant.module.auth.model.vo;

import lombok.Data;

@Data
public class UserInfoVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private String role;
}
