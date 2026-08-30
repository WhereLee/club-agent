package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 讨论消息 VO（落库后广播 + 历史拉取同构） */
@Data
public class ChatMessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    private String senderName;

    private String content;

    private LocalDateTime createdAt;
}