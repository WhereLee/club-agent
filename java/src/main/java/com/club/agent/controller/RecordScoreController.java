package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.RecordScoreDTO;
import com.club.agent.service.RecordScoreService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.RecordScoreVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 留痕打分（块 H）：Java AI 预评（建议值不落库）+ 管理员确认打分（落库）。
 * 窗口：留痕中(7)；未提交留痕 1052；重复打分 1049。
 */
@Tag(name = "留痕打分")
@Validated
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}")
@RequiredArgsConstructor
public class RecordScoreController {

    private final RecordScoreService recordScoreService;

    @PostMapping("/record-scores/preview")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 10)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "AI 预评留痕分（Java Spring AI 单次线性；建议值不落库）")
    public R<RecordScoreVO> preview(@PathVariable Long clubId, @PathVariable Long id,
                                    @RequestParam Long userId) {
        return R.ok(recordScoreService.preview(clubId, id, userId));
    }

    @PostMapping("/record-scores")
    @Log(module = "活动", operation = "留痕打分")
    @RateLimiter(limit = 20, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "留痕打分落库（重复打分 1049）")
    public R<Void> score(@PathVariable Long clubId, @PathVariable Long id,
                         @RequestBody @Valid RecordScoreDTO dto) {
        recordScoreService.score(clubId, id, SecurityUtils.getUserId(), dto);
        return R.ok();
    }

    @GetMapping("/record-scores")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "留痕打分列表（管理层）")
    public R<List<RecordScoreVO>> list(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(recordScoreService.list(clubId, id));
    }
}
