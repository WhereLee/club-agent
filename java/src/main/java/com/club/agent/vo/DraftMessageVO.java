package com.club.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 起草会话消息（前端聊天窗渲染用）。
 */
@Data
public class DraftMessageVO {

    private Long id;

    private Long conceptId;

    private Long userId;

    /** user / assistant / tool */
    private String role;

    private String content;

    private String toolName;

    /** 工具参数 JSON（D2 起：generate_draft 的草案 JSON，前端渲染草案卡片） */
    private String toolArgs;

    private LocalDateTime createdAt;
}
