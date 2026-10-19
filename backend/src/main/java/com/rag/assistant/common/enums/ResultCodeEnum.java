package com.rag.assistant.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCodeEnum {

    SUCCESS(200, "操作成功"),

    PARAM_ERROR(4000, "参数错误"),
    INVALID_TOKEN(4001, "无效的令牌"),
    TOKEN_EXPIRED(4002, "令牌已过期"),
    UNAUTHORIZED(4003, "未登录或登录已失效"),
    FORBIDDEN(4004, "没有操作权限"),
    RESOURCE_NOT_FOUND(4005, "资源不存在"),
    DUPLICATE_RESOURCE(4006, "资源已存在"),
    FILE_TOO_LARGE(4007, "文件大小超过限制"),
    UNSUPPORTED_FILE_TYPE(4008, "不支持的文件类型"),
    PARSE_FAILED(4009, "文档解析失败"),
    RATE_LIMIT(4010, "请求过于频繁，请稍后再试"),
    INVALID_CREDENTIALS(4011, "用户名或密码错误"),
    USER_DISABLED(4012, "账号已被禁用"),
    USERNAME_EXISTS(4013, "用户名已存在"),
    EMAIL_EXISTS(4014, "邮箱已被注册"),

    SYSTEM_ERROR(5000, "服务器内部错误"),
    SERVICE_UNAVAILABLE(5001, "AI服务暂不可用"),
    DB_ERROR(5002, "数据库操作失败"),
    ;

    private final int code;
    private final String message;
}
