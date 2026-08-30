package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 正式文件会话消息 VO（对话面板渲染用；与 DraftMessageVO 同构，activityId 语义）。
 */
@Data
public class FileDraftMessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** user / assistant / tool */
    private String role;

    private String content;

    private String toolName;

    /** 工具参数 JSON（generate_file_draft 的章节草稿 JSON，前端渲染"采纳"按钮） */
    private String toolArgs;

    private LocalDateTime createdAt;
}