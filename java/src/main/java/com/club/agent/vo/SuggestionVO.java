package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 建议候选（管理层视图）：AI 提炼 + 采纳状态 */
@Data
public class SuggestionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    private String senderNickname;

    private String summary;

    private String content;

    private Boolean adopted;

    private LocalDateTime adoptedAt;

    private LocalDateTime createdAt;
}
