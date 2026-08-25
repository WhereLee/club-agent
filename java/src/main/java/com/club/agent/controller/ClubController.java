package com.club.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.AppointDTO;
import com.club.agent.dto.ClubCreateDTO;
import com.club.agent.service.ClubService;
import com.club.agent.service.MembershipService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.ClubDetailVO;
import com.club.agent.vo.ClubVO;
import com.club.agent.vo.MemberVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * 社团与成员接口。
 * 权限模型：创建/审批/任命等操作经 @ClubPermission 校验"社团内角色权限"，
 * 仅"申请加入 / 列表 / 详情 / 离职"是本人动作（按身份自校验）。
 */
@Tag(name = "社团与成员")
@Validated
@RestController
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;
    private final MembershipService membershipService;

    @PostMapping
    @Log(module = "社团", operation = "创建社团")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @Operation(summary = "创建社团（仅指导老师）")
    public R<ClubVO> create(@Valid @RequestBody ClubCreateDTO dto) {
        return R.ok(clubService.create(dto, SecurityUtils.getUserId()));
    }

    @GetMapping
    @Operation(summary = "社团列表（分页）")
    public R<IPage<ClubVO>> list(@RequestParam(defaultValue = "1") @Min(1) long page,
                                 @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return R.ok(clubService.list(page, size));
    }

    @GetMapping("/{clubId}")
    @Operation(summary = "社团详情（含我的身份状态）")
    public R<ClubDetailVO> detail(@PathVariable Long clubId) {
        return R.ok(clubService.detail(clubId, SecurityUtils.getUserId()));
    }

    @PostMapping("/{clubId}/apply")
    @Log(module = "社团", operation = "申请加入社团")
    @RateLimiter(limit = 30, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @Operation(summary = "申请加入（学生）")
    public R<Void> apply(@PathVariable Long clubId) {
        membershipService.apply(clubId, SecurityUtils.getUserId());
        return R.ok();
    }

    @GetMapping("/{clubId}/members")
    @ClubPermission(clubId = "#clubId", permission = "club:member:approve")
    @Operation(summary = "成员列表（含待审批，老师/管理层可见）")
    public R<List<MemberVO>> members(@PathVariable Long clubId) {
        return R.ok(membershipService.listMembers(clubId));
    }

    @PostMapping("/{clubId}/members/{membershipId}/approve")
    @Log(module = "社团", operation = "审批成员加入")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "club:member:approve")
    @Operation(summary = "审批通过（老师/管理层）")
    public R<Void> approve(@PathVariable Long clubId, @PathVariable Long membershipId) {
        membershipService.approve(clubId, membershipId, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{clubId}/members/{membershipId}/reject")
    @Log(module = "社团", operation = "拒绝成员申请")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "club:member:approve")
    @Operation(summary = "拒绝申请（被拒者可重新申请）")
    public R<Void> reject(@PathVariable Long clubId, @PathVariable Long membershipId) {
        membershipService.reject(clubId, membershipId, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{clubId}/members/{membershipId}/appoint")
    @Log(module = "社团", operation = "任命管理层")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "club:member:appoint")
    @Operation(summary = "任命社长/副社长（仅老师，只进空位）")
    public R<Void> appoint(@PathVariable Long clubId, @PathVariable Long membershipId,
                           @Valid @RequestBody AppointDTO dto) {
        membershipService.appoint(clubId, membershipId, dto.getRole(), SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{clubId}/resign")
    @Log(module = "社团", operation = "管理层离职")
    @RepeatSubmit(intervalSeconds = 3)
    @Operation(summary = "管理层离职（本人，角色降为社员）")
    public R<Void> resign(@PathVariable Long clubId) {
        membershipService.resign(clubId, SecurityUtils.getUserId());
        return R.ok();
    }
}
