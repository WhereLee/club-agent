package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.SurveyPublishDTO;
import com.club.agent.dto.SurveySubmitDTO;
import com.club.agent.service.SurveyService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.SurveyResultVO;
import com.club.agent.vo.SurveyVO;
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

/**
 * 问卷（活动前公示环节）：发布 / 详情 / 提交 / 结果 / 结束进入讨论。
 * 权限模型：发布与结果 = 管理层（activity:manage，发起人本人校验在 Service）；详情与提交 = 本社团成员（club:member）。
 */
@Tag(name = "活动问卷")
@Validated
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}/survey")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @PostMapping
    @Log(module = "活动", operation = "发布问卷")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "发布问卷（发起人本人；公示中→问卷中；含系统内置'是否感兴趣'必答题）")
    public R<SurveyVO> publish(@PathVariable Long clubId, @PathVariable Long id,
                               @RequestBody @Valid SurveyPublishDTO dto) {
        return R.ok(surveyService.publish(clubId, id, SecurityUtils.getUserId(), dto));
    }

    @GetMapping
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "问卷详情（成员视角：字段定义 + 我的提交状态；不含他人答案）")
    public R<SurveyVO> detail(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(surveyService.detail(clubId, id, SecurityUtils.getUserId()));
    }

    @PostMapping("/submit")
    @Log(module = "活动", operation = "提交问卷")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "提交问卷（成员；一人一份不可重复；截止后拒绝）")
    public R<Void> submit(@PathVariable Long clubId, @PathVariable Long id,
                          @RequestBody SurveySubmitDTO dto) {
        surveyService.submit(clubId, id, SecurityUtils.getUserId(), dto);
        return R.ok();
    }

    @GetMapping("/results")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "问卷结果（管理层；提交总数 + 选项题计数 / 文本题答案列表）")
    public R<SurveyResultVO> results(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(surveyService.result(clubId, id));
    }

    @PostMapping("/close")
    @Log(module = "活动", operation = "结束问卷进入讨论")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "结束问卷开启讨论（发起人本人；问卷中→讨论中；模板关闭不可再提交）")
    public R<Void> close(@PathVariable Long clubId, @PathVariable Long id) {
        surveyService.startDiscuss(clubId, id, SecurityUtils.getUserId());
        return R.ok();
    }
}