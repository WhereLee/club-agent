package com.club.agent.dto;

import lombok.Data;

/**
 * AI 草案采纳（前端"采纳"按钮触发；所有字段可选——只更新非 null 字段）。
 * note：决策说明/采纳摘要，写入 trace detail（留痕"人采纳了 AI 产出"）。
 */
@Data
public class AiDraftDTO {

    private String reason;

    private String plannedTime;

    private String plannedLocation;

    private String content;

    private String note;
}
