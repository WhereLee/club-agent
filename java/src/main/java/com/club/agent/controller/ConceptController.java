package com.club.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.ConceptDraftDTO;
import com.club.agent.dto.ConceptReviewDTO;
import com.club.agent.dto.ConceptVoteDTO;
import com.club.agent.service.ConceptAiService;
import com.club.agent.service.ConceptService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.ConceptVO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 概念：发起 / 列表 / 详情 / 保存草稿 / 提交 / 撤回 / 放弃。
 * 权限模型：全部要求"该社团管理层"（activity:manage）；
 * 起草/提交/撤回/放弃额外校验"发起者本人"；状态流转走 CAS。
 */
@Tag(name = "概念（活动酝酿）")
@Validated
@RestController
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ConceptController {

    private final ConceptService conceptService;
    private final ConceptAiService conceptAiService;

    @PostMapping("/{clubId}/concepts")
    @Log(module = "概念", operation = "发起概念")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "发起概念（管理层，一个社团同一时间仅一个活跃概念）")
    public R<ConceptVO> create(@PathVariable Long clubId, @RequestBody(required = false) ConceptDraftDTO dto) {
        return R.ok(conceptService.create(clubId, SecurityUtils.getUserId(), dto));
    }

    @GetMapping("/{clubId}/concepts")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "概念列表（管理层可见，分页 + 状态筛选）")
    public R<IPage<ConceptVO>> list(@PathVariable Long clubId,
                                    @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long page,
                                    @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于 1") @Max(value = 100, message = "每页最多 100 条") long size,
                                    @RequestParam(required = false) Integer status) {
        return R.ok(conceptService.list(clubId, SecurityUtils.getUserId(), page, size, status));
    }

    @GetMapping("/{clubId}/concepts/{id}")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "概念详情（含发起人昵称）")
    public R<ConceptVO> detail(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(conceptService.detail(clubId, id));
    }

    @PutMapping("/{clubId}/concepts/{id}/draft")
    @Log(module = "概念", operation = "保存概念草稿")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "保存草稿（发起者本人，起草中；乐观锁防并发覆盖）")
    public R<ConceptVO> saveDraft(@PathVariable Long clubId, @PathVariable Long id,
                                  @RequestBody ConceptDraftDTO dto) {
        return R.ok(conceptService.saveDraft(clubId, id, SecurityUtils.getUserId(), dto));
    }

    @PostMapping("/{clubId}/concepts/{id}/submit")
    @Log(module = "概念", operation = "提交概念")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "提交（发起理由/时间/地点/简述必填，等待管理层审阅，时限 36h）")
    public R<ConceptVO> submit(@PathVariable Long clubId, @PathVariable Long id) {
        ConceptVO vo = conceptService.submit(clubId, id, SecurityUtils.getUserId());
        // D3：提交事务已提交，异步生成"发起人思路"简析（不阻塞提交；失败仅日志）
        conceptAiService.asyncGenerateBrief(id);
        return R.ok(vo);
    }

    @PostMapping("/{clubId}/concepts/{id}/withdraw")
    @Log(module = "概念", operation = "撤回概念")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "撤回（发起人，审批链节点回起草中，已投的票作废）")
    public R<ConceptVO> withdraw(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(conceptService.withdraw(clubId, id, SecurityUtils.getUserId()));
    }

    @PostMapping("/{clubId}/concepts/{id}/abandon")
    @Log(module = "概念", operation = "放弃概念")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "放弃（发起人，任意非终局状态 → 作废）")
    public R<ConceptVO> abandon(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(conceptService.abandon(clubId, id, SecurityUtils.getUserId()));
    }

    @PostMapping("/{clubId}/concepts/{id}/vote")
    @Log(module = "概念", operation = "概念投票")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "管理层投票（发起人不投；赞成/拒绝必填理由；两票齐后按结果推进）")
    public R<ConceptVO> vote(@PathVariable Long clubId, @PathVariable Long id,
                             @RequestBody @Valid ConceptVoteDTO dto) {
        return R.ok(conceptService.vote(clubId, id, SecurityUtils.getUserId(), dto));
    }

    @PostMapping("/{clubId}/concepts/{id}/review")
    @Log(module = "概念", operation = "老师批复概念")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "指导老师批复（通过→活动成立；否决必填理由→作废）")
    public R<ConceptVO> review(@PathVariable Long clubId, @PathVariable Long id,
                               @RequestBody @Valid ConceptReviewDTO dto) {
        return R.ok(conceptService.teacherReview(clubId, id, SecurityUtils.getUserId(), dto));
    }
}
