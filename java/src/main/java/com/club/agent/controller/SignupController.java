package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.SignupDTO;
import com.club.agent.service.SignupService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.SignupMemberVO;
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
 * 报名（块 F）：成员报名（club:member）/ 名单（管理层 activity:manage）。
 * 拦截与截止校验在 Service（1044/1045）。
 */
@Tag(name = "活动报名")
@Validated
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    @PostMapping("/signup")
    @Log(module = "活动", operation = "报名")
    @RateLimiter(limit = 20, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "报名/修改报名（报名中且截止前；不感兴趣者限制参加，在线协助放行）")
    public R<Void> signup(@PathVariable Long clubId, @PathVariable Long id,
                          @RequestBody @Valid SignupDTO dto) {
        signupService.signup(clubId, id, SecurityUtils.getUserId(), dto.getChoice(), dto.getOnlineAssist());
        return R.ok();
    }

    @GetMapping("/signups")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "报名名单（管理层：全员报名状态 + 不感兴趣拦截标记）")
    public R<List<SignupMemberVO>> list(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(signupService.list(clubId, id));
    }
}
