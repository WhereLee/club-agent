package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 概念起草会话：AI 对话消息留痕（人/AI/工具三方）。
 * 业务事实源（审计与续聊恢复）；LangGraph checkpoint 只是运行态缓存。
 * tool_args / form_snapshot 为 JSONB，读取时经 mapper XML ::text 转字符串。
 */
@Data
@TableName("concept_draft_session")
public class ConceptDraftSession {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long conceptId;

    /** 发起人 */
    private Long userId;

    /** user / assistant / tool */
    private String role;

    private String content;

    /** D2 起工具调用记录 */
    private String toolName;

    /** JSONB（D2 起） */
    private String toolArgs;

    /** JSONB（表单快照，D2 起） */
    private String formSnapshot;

    private Integer tokensIn;

    private Integer tokensOut;

    private LocalDateTime createdAt;
}
