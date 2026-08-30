package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.ExperienceSaveDTO;
import com.club.agent.entity.ConceptSession;
import com.club.agent.entity.ExperienceEntry;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ConceptSessionMapper;
import com.club.agent.mapper.ExperienceEntryMapper;
import com.club.agent.service.ExperienceService;
import com.club.agent.vo.ExperienceSearchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 经验域实现：Agent 检索（B1 数据水位）+ 人确认后沉淀（AI 无写权限，D3 边界）。
 * 校验链：来源概念归属社团 + 沉淀人必须是发起人本人（经验来自本人对话，可追溯）。
 */
@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl implements ExperienceService {

    private final ExperienceEntryMapper experienceEntryMapper;
    private final ConceptSessionMapper conceptSessionMapper;

    @Override
    public ExperienceSearchVO experience(Long clubId, Long userId, String q) {
        // D3：真实检索 = 本社团（含通用）有效经验 + 该发起人专属 thinking_pattern（每轮注入）
        List<ExperienceEntry> entries = experienceEntryMapper.selectForAgent(clubId, userId, q, 10);
        ExperienceSearchVO vo = new ExperienceSearchVO();
        // B1：数据水位——本社团（含通用）非思考角度的经验总数（不随关键词变化）
        vo.setSimilarActivityCount(experienceEntryMapper.countForAgent(clubId));
        vo.setItems(entries.stream().map(e -> {
            ExperienceSearchVO.Item it = new ExperienceSearchVO.Item();
            it.setCategory(e.getCategory());
            it.setTitle(e.getTitle());
            it.setContent(e.getContent());
            return it;
        }).toList());
        return vo;
    }

    @Override
    public void saveExperience(Long clubId, Long userId, ExperienceSaveDTO dto) {
        if (dto.getSourceConceptId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        // 来源概念必须属于该社团，且沉淀人必须是发起人本人（经验来自本人对话，可追溯）
        ConceptSession concept = conceptSessionMapper.selectById(dto.getSourceConceptId());
        if (concept == null || !concept.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_CONCEPT_NOT_FOUND);
        }
        if (!concept.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        String category = StringUtils.hasText(dto.getCategory()) ? dto.getCategory() : ExperienceEntry.CATEGORY_CONTEXT;
        if (ExperienceEntry.CATEGORY_THINKING_PATTERN.equals(category) && dto.getOwnerId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR);  // 思考角度必须归属发起人
        }
        if (!StringUtils.hasText(dto.getTitle()) || !StringUtils.hasText(dto.getContent())) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        ExperienceEntry e = new ExperienceEntry();
        e.setId(IdWorker.getId());
        e.setClubId(clubId);
        e.setCategory(category);
        e.setTitle(dto.getTitle().trim());
        e.setContent(dto.getContent().trim());
        e.setOwnerId(dto.getOwnerId());
        e.setSourceConceptId(dto.getSourceConceptId());
        e.setSourceUserId(userId);
        e.setStatus(ExperienceEntry.STATUS_VALID);
        experienceEntryMapper.insert(e);
    }
}
