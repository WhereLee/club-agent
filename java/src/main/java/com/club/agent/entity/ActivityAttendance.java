package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动签到（块 G，2026-08-30）：执行中开放，仅报名参加者可签。
 * uk(activity_id, user_id) 一人一条，重复签到幂等。
 */
@Data
@TableName("activity_attendance")
public class ActivityAttendance {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    private Long userId;

    private LocalDateTime checkedAt;

    private LocalDateTime createdAt;
}
