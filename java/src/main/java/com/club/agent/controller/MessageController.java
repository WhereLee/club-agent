package com.club.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.common.R;
import com.club.agent.service.MessageService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息（概念作废/通过通知）：所有登录用户可查自己的消息。
 */
@Tag(name = "站内消息")
@Validated
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    @Operation(summary = "我的消息分页（可选按已读状态筛选）")
    public R<IPage<MessageVO>> list(@RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long page,
                                    @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于 1") @Max(value = 100, message = "每页最多 100 条") long size,
                                    @RequestParam(required = false) Integer readFlag) {
        return R.ok(messageService.list(SecurityUtils.getUserId(), page, size, readFlag));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读消息数（顶栏红点）")
    public R<Long> unreadCount() {
        return R.ok(messageService.unreadCount(SecurityUtils.getUserId()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记已读（只能操作自己的消息）")
    public R<Void> markRead(@PathVariable Long id) {
        messageService.markRead(SecurityUtils.getUserId(), id);
        return R.ok();
    }
}
