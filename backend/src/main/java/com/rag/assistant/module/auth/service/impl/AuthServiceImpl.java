package com.rag.assistant.module.auth.service.impl;

import com.rag.assistant.common.constant.CacheConstant;
import com.rag.assistant.common.enums.ResultCodeEnum;
import com.rag.assistant.common.exception.BusinessException;
import com.rag.assistant.common.util.JwtUtil;
import com.rag.assistant.module.auth.model.dto.LoginRequest;
import com.rag.assistant.module.auth.model.dto.RegisterRequest;
import com.rag.assistant.module.auth.model.vo.LoginVO;
import com.rag.assistant.module.auth.model.vo.UserInfoVO;
import com.rag.assistant.module.auth.service.AuthService;
import com.rag.assistant.module.user.mapper.UserMapper;
import com.rag.assistant.thirdparty.minio.MinioService;
import com.rag.assistant.module.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final MinioService minioService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void register(RegisterRequest request) {
        User existing = userMapper.selectByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException(ResultCodeEnum.USERNAME_EXISTS);
        }
        if (request.getEmail() != null) {
            User emailUser = userMapper.selectByEmail(request.getEmail());
            if (emailUser != null) {
                throw new BusinessException(ResultCodeEnum.EMAIL_EXISTS);
            }
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(request.getUsername());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userMapper.selectByUsername(request.getUsername());
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());

        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setEmail(user.getEmail());
        userInfo.setAvatar(user.getAvatar() != null && !user.getAvatar().isEmpty() ? minioService.getFileUrl(user.getAvatar()) : null);
        userInfo.setRole(user.getRole());

        return new LoginVO(accessToken, userInfo);
    }

    @Override
    public void logout(String token) {
        long expire = jwtUtil.isTokenExpired(token) ? 0 :
                jwtUtil.parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
        if (expire > 0) {
            redisTemplate.opsForValue().set(
                    CacheConstant.TOKEN_BLACKLIST + token,
                    "1",
                    expire,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.INVALID_TOKEN);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.INVALID_TOKEN);
        }

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());

        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setEmail(user.getEmail());
        userInfo.setAvatar(user.getAvatar() != null && !user.getAvatar().isEmpty() ? minioService.getFileUrl(user.getAvatar()) : null);
        userInfo.setRole(user.getRole());

        return new LoginVO(newAccessToken, userInfo);
    }
}
