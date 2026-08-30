package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执行留痕打分（块 H）：管理员手动打分 + Java AI 预评（并列展示）。
 * uk(activity_id, user_id)：一人一档，不可重复打分。
 */
@Data
@TableName("activity_record_score")
public class ActivityRecordScore {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    /** 被评人（留痕提交人） */
    private Long userId;

    /** 最终分 0-100 */
    private Integer score;

    /** AI 预评分（并列参考） */
    private Integer aiScore;

    private String aiReason;

    /** 打分管理员 */
    private Long scoreBy;

    private LocalDateTime scoreAt;
}
