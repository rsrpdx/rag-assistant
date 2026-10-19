package com.rag.assistant.module.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.assistant.common.enums.ResultCodeEnum;
import com.rag.assistant.common.exception.BusinessException;
import com.rag.assistant.module.user.mapper.UserMapper;
import com.rag.assistant.module.user.model.entity.User;
import com.rag.assistant.module.user.model.vo.UserProfileVO;
import com.rag.assistant.module.user.service.UserService;
import com.rag.assistant.thirdparty.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final MinioService minioService;

    @Override
    public UserProfileVO getCurrentUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return toProfileVO(user);
    }

    @Override
    public void updateProfile(Long userId, String nickname, String email, String phone) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (email != null && !email.equals(user.getEmail())) {
            User existing = userMapper.selectByEmail(email);
            if (existing != null) {
                throw new BusinessException(ResultCodeEnum.EMAIL_EXISTS);
            }
            user.setEmail(email);
        }
        user.setNickname(nickname);
        if (phone != null) user.setPhone(phone);
        userMapper.updateById(user);
    }

    @Override
    public void updateAvatar(Long userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "用户不存在");
        }
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    private UserProfileVO toProfileVO(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar() != null && !user.getAvatar().isEmpty() ? minioService.getFileUrl(user.getAvatar()) : null);
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
