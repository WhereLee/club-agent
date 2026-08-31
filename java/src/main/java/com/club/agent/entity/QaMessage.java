package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答会话消息（人/AI/工具三方）：会话重放与审计的事实源。
 * Python 侧 PostgresSaver checkpoint 只是运行态缓存（模式同 concept_draft_session）。
 */
@Data
@TableName("qa_message")
public class QaMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String role;

    private String content;

    private String toolName;

    private String toolArgs;

    private LocalDateTime createdAt;
}
