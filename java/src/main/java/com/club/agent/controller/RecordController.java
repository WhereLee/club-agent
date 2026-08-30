package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.RecordSubmitDTO;
import com.club.agent.service.RecordService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.RecordMemberVO;
import com.club.agent.vo.RecordVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 执行留痕（块 G）：成员提交（club:member）/ 我的视图 / 管理层列表。
 * 状态与截止校验在 Service（1037/1047/1050）。
 */
@Tag(name = "执行留痕")
@Validated
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    @PostMapping("/records")
    @Log(module = "活动", operation = "提交留痕")
    @RateLimiter(limit = 20, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "提交/修改执行留痕（留痕中且截止前；一人一份覆盖更新）")
    public R<Void> submit(@PathVariable Long clubId, @PathVariable Long id,
                          @RequestBody @Valid RecordSubmitDTO dto) {
        recordService.submit(clubId, id, SecurityUtils.getUserId(), dto);
        return R.ok();
    }

    @GetMapping("/records/mine")
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "我的留痕（模板字段 + 已提交内容回显）")
    public R<RecordVO> mine(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(recordService.mine(clubId, id, SecurityUtils.getUserId()));
    }

    @GetMapping("/records")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "留痕列表（管理层）")
    public R<List<RecordMemberVO>> list(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(recordService.list(clubId, id));
    }
}
