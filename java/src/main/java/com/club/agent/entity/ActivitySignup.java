package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动报名（块 F，2026-08-30）：执行阶段起点。
 * uk(activity_id, user_id) 一人一条，截止前可改（覆盖更新）。
 * 问卷"不感兴趣"者限制参加（participate），在线协助放行（远程支持）。
 */
@Data
@TableName("activity_signup")
public class ActivitySignup {

    public static final String CHOICE_PARTICIPATE = "participate";
    public static final String CHOICE_NOT_PARTICIPATE = "not_participate";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    private Long userId;

    /** participate / not_participate */
    private String choice;

    /** 不参加时勾选在线协助 → 通知发起人 */
    private Boolean onlineAssist;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
