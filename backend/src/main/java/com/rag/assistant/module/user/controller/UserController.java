package com.rag.assistant.module.user.controller;

import com.rag.assistant.common.model.R;
import com.rag.assistant.module.user.model.dto.UpdatePasswordRequest;
import com.rag.assistant.module.user.model.dto.UpdateProfileRequest;
import com.rag.assistant.module.user.model.vo.UserProfileVO;
import com.rag.assistant.module.user.service.UserService;
import com.rag.assistant.security.model.SecurityUser;
import com.rag.assistant.thirdparty.minio.MinioService;
import cn.hutool.core.util.IdUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final MinioService minioService;

    @GetMapping("/profile")
    public R<UserProfileVO> getProfile(@AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(userService.getCurrentUserProfile(securityUser.getUserId()));
    }

    @PutMapping("/profile")
    public R<Void> updateProfile(@AuthenticationPrincipal SecurityUser securityUser,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(securityUser.getUserId(), request.getNickname(), request.getEmail(), request.getPhone());
        return R.ok();
    }

    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                        @AuthenticationPrincipal SecurityUser securityUser) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return R.fail(4001, "文件名不能为空");
        }
        long maxSize = 2 * 1024 * 1024; // 2MB
        if (file.getSize() > maxSize) {
            return R.fail(4001, "头像文件不能超过2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/"))) {
            return R.fail(4001, "仅支持图片格式");
        }
        String objectName = minioService.upload(file, originalName);
        String avatarUrl = minioService.getFileUrl(objectName);
        userService.updateAvatar(securityUser.getUserId(), objectName);
        return R.ok(avatarUrl);
    }

    @PutMapping("/password")
    public R<Void> updatePassword(@AuthenticationPrincipal SecurityUser securityUser,
                                  @Valid @RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(securityUser.getUserId(), request.getOldPassword(), request.getNewPassword());
        return R.ok();
    }
}
