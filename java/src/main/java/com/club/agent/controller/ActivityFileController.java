package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.ActivityFileDTO;
import com.club.agent.service.ActivityFileService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.ActivityFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 正式文件（活动前收尾，块 D）：
 * 草稿保存（发起人本人，讨论中）/ 发布（文件 + 分工 + 状态 3→4 + 通知）/ 查看（发布后全员，草稿仅管理层）。
 */
@Tag(name = "活动正式文件")
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}/file")
@RequiredArgsConstructor
public class ActivityFileController {

    private final ActivityFileService activityFileService;

    @PostMapping("/save")
    @Log(module = "活动", operation = "保存正式文件草稿")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "保存正式文件草稿（发起人本人；讨论中；章节全量覆盖）")
    public R<Void> save(@PathVariable Long clubId, @PathVariable Long id,
                        @RequestBody ActivityFileDTO dto) {
        activityFileService.saveDraft(clubId, id, SecurityUtils.getUserId(), dto);
        return R.ok();
    }

    @PostMapping("/publish")
    @Log(module = "活动", operation = "发布正式文件")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "发布正式文件（文件 + 分工；讨论中→已发布；全员通知 + 指派通知）")
    public R<Void> publish(@PathVariable Long clubId, @PathVariable Long id,
                           @RequestBody ActivityFileDTO dto) {
        activityFileService.publish(clubId, id, SecurityUtils.getUserId(), dto);
        return R.ok();
    }

    @GetMapping
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "查看正式文件（发布后全员；草稿仅发起人/管理层）")
    public R<ActivityFileVO> detail(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(activityFileService.detail(clubId, id, SecurityUtils.getUserId()));
    }
}