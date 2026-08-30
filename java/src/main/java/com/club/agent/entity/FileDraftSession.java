package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 正式文件撰写会话（活动前 Agent，E1）：发起人与 AI 讨论正式文件的业务消息表。
 * 双真相源同概念阶段：LangGraph checkpoint 存图状态，本表存业务消息（事实源）。
 * tool_args JSONB 列：insert 走 XML CAST（K23 先例）。
 */
@Data
@TableName("file_draft_session")
public class FileDraftSession {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    private Long userId;

    /** user / assistant / tool */
    private String role;

    private String content;

    /** 工具名（generate_file_draft 等） */
    private String toolName;

    /** 工具参数 JSON（generate_file_draft 的章节草稿 JSON） */
    private String toolArgs;

    private Integer tokensIn;

    private Integer tokensOut;

    private LocalDateTime createdAt;
}