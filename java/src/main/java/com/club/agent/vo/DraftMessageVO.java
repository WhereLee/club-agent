package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 起草会话消息（前端聊天窗渲染用）。雪花 ID 一律字符串序列化防 JS 精度丢失（K40）。
 */
@Data
public class DraftMessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conceptId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** user / assistant / tool */
    private String role;

    private String content;

    private String toolName;

    /** 工具参数 JSON（D2 起：generate_draft 的草案 JSON，前端渲染草案卡片） */
    private String toolArgs;

    private LocalDateTime createdAt;
}
