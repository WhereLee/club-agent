package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.ConceptChatDTO;
import com.club.agent.service.ActivityFileAiService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.ActivityContextVO;
import com.club.agent.vo.FileDraftMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 正式文件撰写 AI（活动前 Agent，E1）：
 * 对话（发起人本人 + 讨论中，Service 校验）/ 会话回放 / 活动上下文（工具数据源）。
 * AI 能力在 Python（LangGraph），本类为权限代理 + 业务会话落库。
 */
@Tag(name = "活动正式文件 AI")
@Validated
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}")
@RequiredArgsConstructor
public class ActivityFileAiController {

    private final ActivityFileAiService activityFileAiService;

    @PostMapping("/ai/chat")
    @Log(module = "活动AI", operation = "正式文件 AI 对话")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "正式文件 AI 对话（发起人本人，讨论中；工具调用与章节草稿落会话表）")
    public R<List<FileDraftMessageVO>> chat(@PathVariable Long clubId, @PathVariable Long id,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader,
                                            @RequestBody @Valid ConceptChatDTO dto) {
        return R.ok(activityFileAiService.chat(clubId, id, SecurityUtils.getUserId(), dto.getMessage(), authHeader));
    }

    @GetMapping("/ai/session")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "正式文件 AI 会话回放（发起人本人；刷新/换设备恢复）")
    public R<List<FileDraftMessageVO>> session(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(activityFileAiService.session(clubId, id, SecurityUtils.getUserId()));
    }

    @GetMapping("/ai/context")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "活动前置上下文（get_activity_context 工具数据源：概念批复 + 讨论群 + 问卷统计）")
    public R<ActivityContextVO> context(@PathVariable Long clubId, @PathVariable Long id) {
        return R.ok(activityFileAiService.context(clubId, id, SecurityUtils.getUserId()));
    }
}