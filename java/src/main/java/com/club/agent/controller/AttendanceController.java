package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.service.AttendanceService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.AttendanceVO;
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
 * 签到（块 G）：成员签到（club:member）/ 签到名单（activity:manage）。
 * 状态与报名校验在 Service（1037/1046）。
 */
@Tag(name = "活动签到")
@Validated
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/attendance")
    @Log(module = "活动", operation = "签到")
    @RateLimiter(limit = 20, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "签到（执行中开放；仅报名参加者可签，重复签到幂等）")
    public R<Void> checkin(@PathVariable Long clubId, @PathVariable Long id) {
        attendanceService.checkin(clubId, id, SecurityUtils.getUserId());
        return R.ok();
    }

    @GetMapping("/attendances")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "签到名单（管理层：报名参加者 + 签到状态）")
    public R<List<AttendanceVO>> list(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(attendanceService.list(clubId, id));
    }
}
