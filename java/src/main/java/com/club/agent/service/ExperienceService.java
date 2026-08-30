package com.club.agent.service;

import com.club.agent.dto.ExperienceSaveDTO;
import com.club.agent.vo.ExperienceSearchVO;

/**
 * 经验域（D3 起）：Agent 经验检索 + 人确认后沉淀。
 * 边界：AI 无写权限——落库只由前端确认卡片触发；检索带数据水位（B1）。
 */
public interface ExperienceService {

    /** Agent 检索（本社团含通用 + 该发起人 thinking_pattern 注入；q 可空；B1 返回数据水位） */
    ExperienceSearchVO experience(Long clubId, Long userId, String q);

    /** 经验沉淀（人确认后写：发起人本人 + 来源概念归属；thinking_pattern 必带 ownerId） */
    void saveExperience(Long clubId, Long userId, ExperienceSaveDTO dto);
}
