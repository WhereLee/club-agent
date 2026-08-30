package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.service.AiSuggestionService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.SuggestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 讨论建议（块 H）：Java AI 提炼（单次线性）+ 发起人采纳。
 * 数据源为讨论关闭后的高质量消息；计分链 messageId → 建议人。
 */
@Tag(name = "讨论建议")
@Validated
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}")
@RequiredArgsConstructor
public class SuggestionController {

    private final AiSuggestionService suggestionService;

    @PostMapping("/suggestions/extract")
    @Log(module = "活动", operation = "AI 提炼建议")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 10)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "AI 提炼讨论建议（Java Spring AI 单次线性；幂等）")
    public R<List<SuggestionVO>> extract(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(suggestionService.extract(clubId, id));
    }

    @PostMapping("/suggestions/{suggestionId}/adopt")
    @Log(module = "活动", operation = "采纳建议")
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "采纳建议（重复采纳 1048）")
    public R<Void> adopt(@PathVariable Long clubId, @PathVariable Long id, @PathVariable Long suggestionId) {
        suggestionService.adopt(clubId, id, SecurityUtils.getUserId(), suggestionId);
        return R.ok();
    }

    @GetMapping("/suggestions")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "建议列表（管理层）")
    public R<List<SuggestionVO>> list(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(suggestionService.list(clubId, id));
    }
}
