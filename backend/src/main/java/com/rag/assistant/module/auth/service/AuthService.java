package com.rag.assistant.module.auth.service;

import com.rag.assistant.module.auth.model.dto.LoginRequest;
import com.rag.assistant.module.auth.model.dto.RegisterRequest;
import com.rag.assistant.module.auth.model.vo.LoginVO;

public interface AuthService {

    void register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    void logout(String token);

    LoginVO refreshToken(String refreshToken);
}
