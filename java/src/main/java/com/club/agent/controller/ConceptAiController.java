package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.AiDraftDTO;
import com.club.agent.dto.ConceptChatDTO;
import com.club.agent.dto.ExperienceSaveDTO;
import com.club.agent.dto.SkillSaveDTO;
import com.club.agent.service.ConceptAiService;
import com.club.agent.service.ExperienceService;
import com.club.agent.service.KnowledgeService;
import com.club.agent.service.SkillService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.ClubContextVO;
import com.club.agent.vo.ConceptVO;
import com.club.agent.vo.DraftMessageVO;
import com.club.agent.vo.ExperienceSearchVO;
import com.club.agent.vo.KnowledgeSearchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 概念 AI 起草助手（对话式构思 → 表单落地）。
 * 权限模型：管理层 + 发起人本人 + 起草中状态（service 内校验）。
 */
@Tag(name = "概念 AI 起草助手")
@Validated
@RestController
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ConceptAiController {

    private final ConceptAiService conceptAiService;
    private final ExperienceService experienceService;
    private final KnowledgeService knowledgeService;
    private final SkillService skillService;

    @PostMapping("/{clubId}/concepts/{id}/ai/chat")
    @Log(module = "概念AI", operation = "AI 起草对话")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "AI 起草对话（发起人本人，起草中；返回本轮后完整会话）")
    public R<List<DraftMessageVO>> chat(@PathVariable Long clubId, @PathVariable Long id,
                                        @RequestHeader(value = "Authorization", required = false) String authHeader,
                                        @RequestBody @Valid ConceptChatDTO dto) {
        return R.ok(conceptAiService.chat(clubId, id, SecurityUtils.getUserId(), dto.getMessage(), authHeader));
    }

    @GetMapping("/{clubId}/concepts/{id}/ai/session")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "会话重放（发起人本人；页面刷新/换设备恢复）")
    public R<List<DraftMessageVO>> session(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(conceptAiService.session(clubId, id, SecurityUtils.getUserId()));
    }

    @GetMapping("/{clubId}/ai/context")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "社团上下文（get_club_context 工具：简介/管理层/往届概念）")
    public R<ClubContextVO> context(@PathVariable Long clubId) {
        return R.ok(conceptAiService.context(clubId, SecurityUtils.getUserId()));
    }

    @GetMapping("/{clubId}/ai/experience")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "经验检索（search_experience 工具；D3 真实检索 + 该发起人 thinking_pattern 注入）")
    public R<ExperienceSearchVO> experience(@PathVariable Long clubId, @RequestParam String q) {
        return R.ok(experienceService.experience(clubId, SecurityUtils.getUserId(), q));
    }

    @GetMapping("/{clubId}/ai/knowledge")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "双源知识检索（双项目集成：SQL 经验条目 + rag 活动资料；rag 故障降级单源）")
    public R<KnowledgeSearchVO> knowledge(@PathVariable Long clubId,
                                          @RequestParam String q,
                                          @RequestParam(defaultValue = "8") int topK) {
        return R.ok(knowledgeService.knowledge(clubId, SecurityUtils.getUserId(), q,
                Math.max(1, Math.min(topK, 20))));
    }

    @PostMapping("/{clubId}/ai/experience")
    @Log(module = "概念AI", operation = "沉淀经验")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "经验沉淀（人确认后写：发起人本人 + 来源概念归属校验；AI 无写权限）")
    public R<Void> saveExperience(@PathVariable Long clubId, @RequestBody @Valid ExperienceSaveDTO dto) {
        experienceService.saveExperience(clubId, SecurityUtils.getUserId(), dto);
        return R.ok();
    }

    @PostMapping("/{clubId}/ai/skill")
    @Log(module = "概念AI", operation = "落盘 SKILL")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "SKILL.md 落盘（人确认后写：发起人本人 + name 白名单防穿越；返回落盘路径）")
    public R<String> saveSkill(@PathVariable Long clubId, @RequestBody @Valid SkillSaveDTO dto) {
        return R.ok(skillService.saveSkill(clubId, SecurityUtils.getUserId(), dto));
    }

    @PutMapping("/{clubId}/concepts/{id}/ai-draft")
    @Log(module = "概念AI", operation = "采纳 AI 草案")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "采纳 AI 草案（人确认前置：发起人本人，起草中；更新非 null 字段 + trace ai_draft）")
    public R<ConceptVO> applyAiDraft(@PathVariable Long clubId, @PathVariable Long id,
                                     @RequestBody AiDraftDTO dto) {
        return R.ok(conceptAiService.applyAiDraft(clubId, id, SecurityUtils.getUserId(), dto));
    }
}
