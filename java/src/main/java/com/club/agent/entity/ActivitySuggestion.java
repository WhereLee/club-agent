package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 讨论建议候选（块 H）：Java AI 从高质量消息提炼，发起人采纳计质量分。
 * uk(activity_id, message_id)：同一条消息只提炼一次。
 */
@Data
@TableName("activity_suggestion")
public class ActivitySuggestion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    /** 来源消息（计分链：建议人） */
    private Long messageId;

    private Long senderId;

    /** AI 提炼要点 */
    private String summary;

    /** 消息原文（参考） */
    private String content;

    private Boolean adopted;

    private LocalDateTime adoptedAt;

    private Long adoptedBy;

    private LocalDateTime createdAt;
}
