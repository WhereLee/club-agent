package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.common.R;
import com.club.agent.service.RewardService;
import com.club.agent.vo.RewardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 奖励统计（块 H）：双标准打分（频率 + 质量）→ 总分 → 等级。
 */
@Tag(name = "活动奖励")
@Validated
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    @GetMapping("/rewards")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "奖励统计（全员总分 + 等级）")
    public R<List<RewardVO>> rewards(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(rewardService.rewards(clubId, id));
    }
}
