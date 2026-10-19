package com.rag.assistant.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.assistant.module.user.model.entity.User;
import com.rag.assistant.module.user.model.vo.UserProfileVO;

public interface UserService extends IService<User> {

    UserProfileVO getCurrentUserProfile(Long userId);

    void updateProfile(Long userId, String nickname, String email, String phone);

    void updatePassword(Long userId, String oldPassword, String newPassword);

    void updateAvatar(Long userId, String avatarUrl);
}
