package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息（概念/活动通知，前端以待办聚合展示）。
 */
@Data
@TableName("message")
public class Message {

    public static final String TYPE_CONCEPT_VOID = "concept_void";
    public static final String TYPE_CONCEPT_APPROVED = "concept_approved";
    /** 块 A：活动公示（概念通过 → 全员收初稿） */
    public static final String TYPE_ACTIVITY_ANNOUNCE = "activity_announce";
    /** 块 A：活动取消（发起人取消 → 全员收理由） */
    public static final String TYPE_ACTIVITY_CANCEL = "activity_cancel";
    /** 块 B：问卷发布（发起人发布问卷 → 全员收填写提醒） */
    public static final String TYPE_ACTIVITY_SURVEY = "activity_survey";
    /** 块 C：加入讨论群（问卷截止统一入群 → 入群成员收进群通知） */
    public static final String TYPE_ACTIVITY_DISCUSS = "activity_discuss";
    /** 块 D：正式文件发布（全员收文件；活动确定） */
    public static final String TYPE_ACTIVITY_FILE = "activity_file";
    /** 块 D：分工指派（被指派成员收职责通知） */
    public static final String TYPE_ACTIVITY_DUTY = "activity_duty";
    public static final String TYPE_ACTIVITY_SIGNUP_OPEN = "activity_signup_open";
    public static final String TYPE_ACTIVITY_ONLINE_ASSIST = "activity_online_assist";
    public static final String TYPE_ACTIVITY_RECORD_OPEN = "activity_record_open";
    /** 活动后阶段：归档（总结完成 → 全员可查看总结报告） */
    public static final String TYPE_ACTIVITY_ARCHIVED = "activity_archived";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long recipientId;

    /** concept_void / concept_approved / activity_announce / activity_cancel */
    private String type;

    private String title;

    private String content;

    /** 关联概念（雪花 id） */
    private Long refConceptId;

    /** 关联活动（雪花 id；与 refConceptId 二选一） */
    private Long refActivityId;

    /** 0=未读 1=已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;
}
