package com.club.agent.dto;

import lombok.Data;

/**
 * SKILL.md 落盘（人确认后写）：前端确认卡片触发，AI 无写权限。
 * body 为完整 SKILL.md 内容（frontmatter + 正文）；name 白名单校验（防路径穿越）。
 */
@Data
public class SkillSaveDTO {

    private String name;

    private String description;

    private String whenToUse;

    /** SKILL.md 全文（UTF-8 落盘） */
    private String body;

    /** 来源概念（可追溯） */
    private Long sourceConceptId;
}
