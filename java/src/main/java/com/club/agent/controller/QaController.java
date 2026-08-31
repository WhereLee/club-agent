package com.club.agent.controller;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.service.QaService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.QaMessageVO;
import com.club.agent.vo.QaSessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理层经验问答（双项目集成阶段2 · J3）：独立 Agent 服务的业务入口。
 * 权限：仅管理层（activity:manage）；会话私有（服务内校验本人）。
 * 鉴权链：前端 → Java（JWT + @ClubPermission）→ Python agent_qa（内部密钥 + JWT 透传）
 * → 工具回调 Java /ai/knowledge（@ClubPermission 再校验）→ rag。
 */
@Tag(name = "管理层经验问答")
@Validated
@RestController
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    @PostMapping("/{clubId}/ai/qa/sessions")
    @Log(module = "经验问答", operation = "创建问答会话")
    @RepeatSubmit(intervalSeconds = 3)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "创建问答会话（管理层本人）")
    public R<QaSessionVO> createSession(@PathVariable Long clubId,
                                        @RequestBody(required = false) Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        return R.ok(qaService.createSession(clubId, SecurityUtils.getUserId(), title));
    }

    @GetMapping("/{clubId}/ai/qa/sessions")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "问答会话列表（本人有效会话，最近活跃在前）")
    public R<List<QaSessionVO>> listSessions(@PathVariable Long clubId) {
        return R.ok(qaService.listSessions(clubId, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/{clubId}/ai/qa/sessions/{sessionId}")
    @Log(module = "经验问答", operation = "删除问答会话")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "软删问答会话（本人）")
    public R<Void> deleteSession(@PathVariable Long clubId, @PathVariable Long sessionId) {
        qaService.deleteSession(clubId, SecurityUtils.getUserId(), sessionId);
        return R.ok();
    }

    @PostMapping("/{clubId}/ai/qa/sessions/{sessionId}/chat")
    @Log(module = "经验问答", operation = "经验问答")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 5)
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "单轮问答（本人会话；返回本轮后完整会话消息）")
    public R<List<QaMessageVO>> chat(@PathVariable Long clubId, @PathVariable Long sessionId,
                                     @RequestHeader(value = "Authorization", required = false) String authHeader,
                                     @RequestBody Map<String, @NotBlank @Size(max = 2000) String> body) {
        return R.ok(qaService.chat(clubId, SecurityUtils.getUserId(), sessionId,
                body.get("message"), authHeader));
    }

    @GetMapping("/{clubId}/ai/qa/sessions/{sessionId}/messages")
    @ClubPermission(clubId = "#clubId", permission = "activity:manage")
    @Operation(summary = "会话重放（页面刷新恢复）")
    public R<List<QaMessageVO>> messages(@PathVariable Long clubId, @PathVariable Long sessionId) {
        return R.ok(qaService.messages(clubId, SecurityUtils.getUserId(), sessionId));
    }
}
