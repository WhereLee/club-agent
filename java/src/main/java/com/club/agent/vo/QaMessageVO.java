package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 问答消息（会话重放；角色人/AI/工具三方）。雪花 id 字符串序列化（K40）。 */
@Data
public class QaMessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** user / assistant / tool */
    private String role;

    private String content;

    /** role=tool 时的工具名与入参（审计与前端溯源卡片共用） */
    private String toolName;

    private String toolArgs;

    private LocalDateTime createdAt;
}
