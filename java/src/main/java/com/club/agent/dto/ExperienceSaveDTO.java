package com.club.agent.dto;

import lombok.Data;

/**
 * 经验沉淀（人确认后写）：前端确认卡片触发，AI 无写权限。
 * sourceConceptId 必传（经验都来自对话，可追溯）；ownerId 仅 thinking_pattern 使用。
 */
@Data
public class ExperienceSaveDTO {

    private String category;

    private String title;

    private String content;

    /** 思考角度归属的发起人（可空 = 通用） */
    private Long ownerId;

    private Long sourceConceptId;
}
