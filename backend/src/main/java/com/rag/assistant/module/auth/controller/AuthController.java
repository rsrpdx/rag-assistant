package com.rag.assistant.module.auth.controller;

import com.rag.assistant.common.constant.ApiConstant;
import com.rag.assistant.common.model.R;
import com.rag.assistant.module.auth.model.dto.LoginRequest;
import com.rag.assistant.module.auth.model.dto.RegisterRequest;
import com.rag.assistant.module.auth.model.vo.LoginVO;
import com.rag.assistant.module.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return R.ok("注册成功", null);
    }

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(ApiConstant.TOKEN_HEADER) String authHeader) {
        String token = authHeader.replace(ApiConstant.TOKEN_PREFIX, "");
        authService.logout(token);
        return R.ok();
    }

    @PostMapping("/refresh")
    public R<LoginVO> refresh(@RequestHeader("Refresh-Token") String refreshToken) {
        return R.ok(authService.refreshToken(refreshToken));
    }
}
