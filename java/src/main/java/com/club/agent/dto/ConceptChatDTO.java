package com.club.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 起草对话请求。
 */
@Data
public class ConceptChatDTO {

    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "消息最长 2000 字")
    private String message;
}
