package com.club.agent.dto;

import lombok.Data;

/** 讨论消息发送体（STOMP @MessageMapping 载荷；校验在 Service，@Valid 对 STOMP 不生效） */
@Data
public class ChatSendDTO {

    private String content;
}