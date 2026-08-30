package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动：概念通过（status=5）后自动创建，活动前/中/后三阶段状态机主表。
 * status：1=公示中 2=问卷中 3=讨论中 4=已发布（正式文件定稿，进入活动中） 5=已取消。
 * 唯一性：一个概念只转一个活动（concept_id 唯一约束）。
 */
@Data
@TableName("activity")
public class Activity {

    public static final int STATUS_ANNOUNCING = 1;
    public static final int STATUS_SURVEYING = 2;
    public static final int STATUS_DISCUSSING = 3;
    public static final int STATUS_PUBLISHED = 4;
    public static final int STATUS_SIGNUP = 5;
    public static final int STATUS_EXECUTING = 6;
    public static final int STATUS_RECORDING = 7;
    public static final int STATUS_SUMMARIZING = 8;
    public static final int STATUS_ARCHIVED = 9;
    public static final int STATUS_CANCELLED = 10;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long clubId;

    /** 来源概念（可追溯审批链） */
    private Long conceptId;

    /** 发起人（复制自概念；取消/后续发布动作的权限校验用） */
    private Long userId;

    /** 1=公示中 2=问卷中 3=讨论中 4=已发布 5=已取消 */
    private Integer status;

    private String plannedTime;

    private String plannedLocation;

    /** 初稿简述（复制自概念，公示用） */
    private String content;

    /** 取消理由（必填） */
    private String cancelReason;

    /** 报名截止时间（发起人开始报名时设置；超时锁报名） */
    private LocalDateTime signupDeadline;

    /** 执行留痕提交截止时间（管理层设置；超时自动进总结阶段） */
    private LocalDateTime recordDeadline;

    /** 讨论关闭时间（发起人结束讨论；关闭后才可撰写正式文件） */
    private LocalDateTime discussionClosedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
