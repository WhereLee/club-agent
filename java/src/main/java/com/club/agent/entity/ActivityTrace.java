package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;


import java.time.LocalDateTime;

/**
 * 活动全量流水：谁在什么时候做了什么（时间线展示与审计）。
 * action：create/cancel；块 B/C/D 补充 survey_publish/discuss_start/file_publish。
 */
@Data
@TableName("activity_trace")
public class ActivityTrace {

    /** 概念通过批复自动创建活动（系统动作） */
    public static final String ACTION_CREATE = "create";
    /** 发起人取消活动（detail=取消理由） */
    public static final String ACTION_END_DISCUSSION = "end_discussion";
    public static final String ACTION_START_SIGNUP = "start_signup";
    public static final String ACTION_START_EXECUTION = "start_execution";
    public static final String ACTION_COMPLETE_EXECUTION = "complete_execution";
    public static final String ACTION_RECORD_CLOSE = "record_close";
    public static final String ACTION_ARCHIVE = "archive";
    public static final String ACTION_CANCEL = "cancel";
    /** 块 B：发起人发布问卷（detail=截止时间） */
    public static final String ACTION_SURVEY_PUBLISH = "survey_publish";
    /** 块 B：发起人结束问卷开启讨论（detail=截止说明） */
    public static final String ACTION_DISCUSS_START = "discuss_start";
    /** 块 D：正式文件发布（detail=发布说明） */
    public static final String ACTION_FILE_PUBLISH = "file_publish";

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** NULL=系统动作（如概念转活动） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private String action;

    /** 理由/备注 */
    private String detail;

    private LocalDateTime createdAt;
}
