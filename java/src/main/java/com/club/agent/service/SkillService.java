package com.club.agent.service;

import com.club.agent.dto.SkillSaveDTO;

/**
 * 技能域（D4 起）：SKILL.md 落盘（人确认后写，AI 无写权限）。
 * 校验发起人本人 + 来源概念归属 + name 白名单（kebab-case 防路径穿越）→ 写 skills/{name}/SKILL.md → trace(ai_skill)。
 */
public interface SkillService {

    /** SKILL.md 落盘，返回落盘绝对路径 */
    String saveSkill(Long clubId, Long userId, SkillSaveDTO dto);
}
