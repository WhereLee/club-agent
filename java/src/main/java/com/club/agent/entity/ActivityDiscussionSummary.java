package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 讨论结束快照（活动中阶段，2026-08-30）：
 * 每成员讨论消息数与高质量消息数，讨论关闭时一次性统计（快照语义）。
 * 频率标准的数据源——奖励机制"高频参与分"据此判定（msg_count >= 高频阈值）。
 */
@Data
@TableName("activity_discussion_summary")
public class ActivityDiscussionSummary {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    private Long userId;

    /** 总消息数 */
    private Integer msgCount;

    /** 高质量消息数（word_count >= 10） */
    private Integer qualityCount;

    /** 是否高频讨论者（msg_count >= 高频阈值） */
    @com.baomidou.mybatisplus.annotation.TableField("is_high_freq")
    private Boolean highFreq;

    private LocalDateTime createdAt;
}
