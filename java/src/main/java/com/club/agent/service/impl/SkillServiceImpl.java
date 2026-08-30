package com.club.agent.service.impl;

import com.club.agent.common.ResultCode;
import com.club.agent.dto.SkillSaveDTO;
import com.club.agent.entity.ConceptSession;
import com.club.agent.entity.ConceptTrace;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ConceptSessionMapper;
import com.club.agent.mapper.ConceptTraceMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 技能域实现：SKILL.md 落盘（人确认后写，AI 无写权限，D4 边界）。
 * name 白名单（kebab-case 防路径穿越/非法文件名）；落盘后 trace(ai_skill) 留痕人确认动作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final ConceptSessionMapper conceptSessionMapper;
    private final ConceptTraceMapper conceptTraceMapper;
    private final SysUserMapper sysUserMapper;

    @Value("${ai.draft.skill-dir:../skills}")
    private String skillDir;

    @Override
    public String saveSkill(Long clubId, Long userId, SkillSaveDTO dto) {
        if (dto.getSourceConceptId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        // 来源概念归属 + 发起人本人（延续经验沉淀的校验链）
        ConceptSession concept = conceptSessionMapper.selectById(dto.getSourceConceptId());
        if (concept == null || !concept.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_CONCEPT_NOT_FOUND);
        }
        if (!concept.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        // name 白名单（kebab-case，防路径穿越/非法文件名）；body 必填
        String name = dto.getName() == null ? null : dto.getName().trim();
        if (name == null || !name.matches("[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?")) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        if (!StringUtils.hasText(dto.getBody())) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        try {
            Path dir = Paths.get(skillDir, name);
            Files.createDirectories(dir);
            Path file = dir.resolve("SKILL.md");
            Files.writeString(file, dto.getBody(), StandardCharsets.UTF_8);
            // 留痕：人确认后落盘（trace detail = skill 名）
            ConceptTrace t = new ConceptTrace();
            t.setConceptId(dto.getSourceConceptId());
            t.setOperatorId(userId);
            t.setOperatorName(nicknameOf(userId));
            t.setAction(ConceptTrace.ACTION_AI_SKILL);
            t.setDetail("SKILL 落盘：" + name);
            conceptTraceMapper.insert(t);
            return file.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            log.warn("SKILL 落盘失败：name={} err={}", name, e.getMessage());
            throw new BizException(ResultCode.BIZ_AI_UNAVAILABLE);
        }
    }

    private String nicknameOf(Long userId) {
        return sysUserMapper.selectById(userId) == null ? "发起人" : sysUserMapper.selectById(userId).getNickname();
    }
}
