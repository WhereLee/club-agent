package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 讨论群消息（块 C）：先落库后广播，重连 REST 补拉；sender_name 冗余（历史展示不回查）。
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    private Long senderId;

    /** 冗余昵称（用户改名不影响历史展示） */
    private String senderName;

    private String content;

    /** 去空白后字数（插入时计算） */
    private Integer wordCount;

    /** 低质量标记：字数 < 10 的短回复，不进入文件 Agent 参考集 */
    @com.baomidou.mybatisplus.annotation.TableField("is_low_quality")
    private Boolean lowQuality;

    private LocalDateTime createdAt;
}