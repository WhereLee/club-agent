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
    BIZ_PASSWORD_FORMAT_ERROR(1012, "密码格式不正确（8-32 位，限字母/数字/常见符号）"),

    // ---- 业务码段（1xxx：社团 / 成员） ----
    BIZ_CLUB_NAME_EXISTS(1013, "社团名称已存在"),
    BIZ_ALREADY_MEMBER(1014, "您已是该社团成员"),
    BIZ_ALREADY_APPLIED(1015, "已提交申请，等待审批"),
    BIZ_APPLY_HANDLED(1016, "该申请已处理"),
    BIZ_NOT_CLUB_MEMBER(1017, "您不是该社团成员"),
    BIZ_NOT_MANAGEMENT(1018, "仅管理层可执行该操作"),
    BIZ_PRESIDENT_EXISTS(1019, "该社团已有在职社长，需现任社长先离职"),
    BIZ_VICE_PRESIDENT_FULL(1020, "该社团副社长已满（最多 2 人），需现任副社长先离职"),
    BIZ_INVALID_ROLE(1021, "角色不合法（仅 president / vice_president）"),
    BIZ_TEACHER_ONLY(1022, "仅指导老师可执行该操作"),
    BIZ_APPLY_STUDENT_ONLY(1023, "仅学生可申请加入社团"),
    BIZ_ALREADY_MANAGEMENT(1024, "该用户已是其他社团的管理层，不能重复任命"),
    BIZ_ALREADY_APPOINTED(1025, "该成员已是目标职务，无需重复任命");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
