package com.club.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.ActivityCancelDTO;
import com.club.agent.dto.RecordStartDTO;
import com.club.agent.dto.SignupStartDTO;
import com.club.agent.service.ActivityService;
import com.club.agent.service.SummaryService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.ActivityVO;
import com.club.agent.vo.SummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动（活动前阶段）：列表 / 详情 / 取消。
 * 权限模型：列表/详情 = 本社团成员（club:member，含老师与全体成员）；取消 = 管理层 + 发起人本人（service 内校验）。
 */
@Tag(name = "活动（活动前）")
@Validated
@RestController
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final SummaryService summaryService;

    @GetMapping("/{clubId}/activities")
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "活动列表（本社团全员可见，分页 + 状态筛选）")
    public R<IPage<ActivityVO>> list(@PathVariable Long clubId,
                                     @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long page,
                                     @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于 1") @Max(value = 100, message = "每页最多 100 条") long size,
                                     @RequestParam(required = false) Integer status) {
        return R.ok(activityService.list(clubId, page, size, status));
    }

    @GetMapping("/{clubId}/activities/{id}")
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "活动详情（含时间线 traces）")
    public R<ActivityVO> detail(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(activityService.detail(clubId, id));
    }

    @PostMapping("/{clubId}/activities/{id}/discussion/end")
    @Log(module = "活动", operation = "结束讨论")
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "结束讨论（发起人）：群转只读 + 讨论质量快照，解锁正式文件撰写")
    public R<Void> endDiscussion(@PathVariable Long clubId, @PathVariable Long id) {
        activityService.endDiscussion(clubId, id, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{clubId}/activities/{id}/signup/start")
    @Log(module = "活动", operation = "开始报名")
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "开始报名（发起人）：已发布 → 报名中，需设置报名截止时间")
    public R<Void> startSignup(@PathVariable Long clubId, @PathVariable Long id,
                               @RequestBody @Valid SignupStartDTO dto) {
        activityService.startSignup(clubId, id, SecurityUtils.getUserId(), dto.getDeadline());
        return R.ok();
    }

    @PostMapping("/{clubId}/activities/{id}/execution/start")
    @Log(module = "活动", operation = "开始执行")
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "开始执行（发起人）：报名中 → 执行中（可选留痕截止时间）")
    public R<Void> startExecution(@PathVariable Long clubId, @PathVariable Long id,
                                  @RequestBody(required = false) RecordStartDTO dto) {
        activityService.startExecution(clubId, id, SecurityUtils.getUserId(),
                dto == null ? null : dto.getDeadline());
        return R.ok();
    }

    @PostMapping("/{clubId}/activities/{id}/execution/complete")
    @Log(module = "活动", operation = "结束执行")
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "结束执行（发起人）：执行中 → 留痕中，开放留痕提交")
    public R<Void> completeExecution(@PathVariable Long clubId, @PathVariable Long id) {
        activityService.completeExecution(clubId, id, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{clubId}/activities/{id}/records/close")
    @Log(module = "活动", operation = "关闭留痕")
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "关闭留痕（发起人）：留痕中 → 总结中（进入后自动生成活动总结）")
    public R<Void> closeRecords(@PathVariable Long clubId, @PathVariable Long id) {
        activityService.closeRecords(clubId, id, SecurityUtils.getUserId(), false);
        return R.ok();
    }

    @PostMapping("/{clubId}/activities/{id}/cancel")
    @Log(module = "活动", operation = "取消活动")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "取消活动（发起人本人；必填理由 → 已取消 + 全员通知）")
    public R<Void> cancel(@PathVariable Long clubId, @PathVariable Long id,
                          @RequestBody @Valid ActivityCancelDTO dto) {
        activityService.cancel(clubId, id, SecurityUtils.getUserId(), dto.getReason());
        return R.ok();
    }

    @PostMapping("/{clubId}/activities/{id}/archive")
    @Log(module = "活动", operation = "归档活动")
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "归档（发起人）：总结中 → 已归档（前置：总结已生成）")
    public R<Void> archive(@PathVariable Long clubId, @PathVariable Long id) {
        activityService.archive(clubId, id, SecurityUtils.getUserId());
        return R.ok();
    }

    @GetMapping("/{clubId}/activities/{id}/summary")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "活动总结详情（管理层视图：指标 + AI 总结 + 待确认问题）")
    public R<SummaryVO> summary(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(summaryService.detail(clubId, id));
    }

    @PostMapping("/{clubId}/activities/{id}/summary/regenerate")
    @Log(module = "活动", operation = "重新生成活动总结")
    @RepeatSubmit(intervalSeconds = 10)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "重新生成活动总结（发起人；覆盖旧报告，归档后亦可用）")
    public R<Void> regenerateSummary(@PathVariable Long clubId, @PathVariable Long id) {
        summaryService.generate(clubId, id, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{clubId}/activities/{id}/summary/resume")
    @Log(module = "活动", operation = "回答总结待确认问题")
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "提交待确认问题回答后恢复总结生成（发起人）")
    public R<Void> resumeSummary(@PathVariable Long clubId, @PathVariable Long id,
                                 @RequestBody Map<String, String> answers) {
        summaryService.resume(clubId, id, SecurityUtils.getUserId(), answers);
        return R.ok();
    }
}