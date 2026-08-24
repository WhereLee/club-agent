package com.club.agent.common;

import lombok.Getter;

/**
 * 业务码枚举：框架通用码（2xx/4xx/5xx）+ 业务码段（1xxx）。
 * 新增业务失败场景时在此登记，保证前端/文档可枚举。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    FAIL(500, "操作失败"),

    // ---- 业务码段（1xxx：认证 / 用户） ----
    BIZ_USERNAME_EXISTS(1001, "用户名已存在"),
    BIZ_EMAIL_EXISTS(1002, "邮箱已被注册"),
    BIZ_USERNAME_OR_PASSWORD_ERROR(1003, "用户名或密码错误"),
    BIZ_USER_DISABLED(1004, "账号已被禁用，请联系指导老师"),
    BIZ_ACCOUNT_LOCKED(1005, "失败次数过多，账号已锁定，请稍后再试"),
    BIZ_CAPTCHA_ERROR(1006, "验证码错误或已过期"),
    BIZ_USER_NOT_FOUND(1007, "用户不存在"),
    BIZ_OLD_PASSWORD_ERROR(1008, "原密码错误"),
    BIZ_FILE_TOO_LARGE(1009, "文件大小超出限制"),
    BIZ_FILE_TYPE_ERROR(1010, "文件类型不支持"),
    BIZ_UPLOAD_FAIL(1011, "文件上传失败"),
    BIZ_PASSWORD_FORMAT_ERROR(1012, "密码格式不正确（8-32 位，限字母/数字/常见符号）");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
