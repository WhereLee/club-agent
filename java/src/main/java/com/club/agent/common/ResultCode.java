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
    BIZ_ALREADY_APPOINTED(1025, "该成员已是目标职务，无需重复任命"),

    // ---- 业务码段（1xxx：概念诞生） ----
    BIZ_CONCEPT_NOT_FOUND(1026, "概念会话不存在"),
    BIZ_CONCEPT_SUBMITTED(1027, "该概念已提交，不可再修改"),
    BIZ_CONCEPT_EMPTY_FIELDS(1028, "预计时间、预计地点、活动简述、发起理由为必填项"),
    BIZ_CONCEPT_ACTIVE_EXISTS(1029, "该社团已有进行中的概念，需先完成或作废当前概念"),
    BIZ_CONCEPT_DRAFT_CONFLICT(1030, "草稿已在其他窗口被修改，请刷新后重试"),
    BIZ_CONCEPT_STATE_FORBIDDEN(1031, "概念当前状态不可执行该操作"),
    BIZ_CONCEPT_ALREADY_VOTED(1032, "您已投过票，不可重复投票"),
    BIZ_CONCEPT_REQUESTER_NO_VOTE(1033, "发起人不参与投票"),
    BIZ_CONCEPT_REVIEW_COMMENT_REQUIRED(1034, "否决概念必须填写理由"),
    BIZ_AI_UNAVAILABLE(1035, "AI 暂不可用，请稍后重试或手动填写"),

    // ---- 业务码段（1xxx：活动前阶段） ----
    BIZ_ACTIVITY_NOT_FOUND(1036, "活动不存在"),
    BIZ_ACTIVITY_STATE_FORBIDDEN(1037, "活动当前状态不可执行该操作"),

    // ---- 业务码段（1xxx：问卷） ----
    BIZ_SURVEY_NOT_FOUND(1038, "问卷不存在或未发布"),
    BIZ_SURVEY_CLOSED(1039, "问卷已截止"),
    BIZ_SURVEY_ALREADY_SUBMITTED(1040, "您已提交过问卷"),
    BIZ_SURVEY_REQUIRED_FIELD(1041, "存在未填写的必答题"),

    // ---- 业务码段（1xxx：讨论群） ----
    BIZ_CHAT_FORBIDDEN(1042, "无权限访问讨论群"),

    // ---- 业务码段（1xxx：正式文件） ----
    BIZ_FILE_NOT_FOUND(1043, "正式文件不存在或尚未撰写"),
    BIZ_SIGNUP_CLOSED(1044, "报名已截止"),
    BIZ_SIGNUP_NOT_INTERESTED(1045, "问卷标记不感兴趣，限制参加本次活动"),
    BIZ_ATTENDANCE_FORBIDDEN(1046, "未报名参加，不可签到"),
    BIZ_RECORD_CLOSED(1047, "执行留痕已截止"),
    BIZ_RECORD_REQUIRED_FIELD(1050, "存在未填写的必填留痕项"),
    BIZ_SUGGESTION_NOT_FOUND(1051, "建议不存在或已删除"),
    BIZ_RECORD_NOT_SUBMITTED(1052, "该成员未提交执行留痕，无法打分"),
    BIZ_SUGGESTION_DUPLICATE(1048, "该建议已被采纳过"),
    BIZ_RECORD_SCORE_DUPLICATE(1049, "已对该留痕打过份，不可重复打分"),

    // ---- 业务码段（1xxx：活动后阶段） ----
    BIZ_SUMMARY_NOT_GENERATED(1053, "活动总结尚未生成，请先生成总结"),
    BIZ_SUMMARY_QUESTIONS_PENDING(1054, "存在未处理的待确认问题，请先回答"),
    BIZ_ARCHIVE_STATE_FORBIDDEN(1055, "活动当前状态不可归档"),

    // ---- 业务码段（1xxx：活动资料库 / 双项目集成） ----
    BIZ_FILE_LIB_RAG_DISABLED(1056, "知识服务未启用，暂不可上传活动资料"),
    BIZ_FILE_LIB_NOT_FOUND(1057, "资料不存在或已删除"),
    BIZ_QA_SESSION_NOT_FOUND(1058, "问答会话不存在或已删除");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
