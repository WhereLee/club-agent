package com.club.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.annotation.ClubPermission;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.ChatSendDTO;
import com.club.agent.service.ChatService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.ChatMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 讨论群（活动前环节，块 C）。
 * REST：历史拉取（重连补拉）+ 群成员；STOMP：/app/chat/activity/{id} 发送（先落库后广播）。
 * 鉴权：订阅/发送/拉历史共用 activity_chat_member 快照；老师不可见（不在名单）。
 */
@Tag(name = "活动讨论群")
@RestController
@RequestMapping("/clubs/{clubId}/activities/{id}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 历史消息（重连补拉；在群才可见） */
    @GetMapping("/messages")
    @ClubPermission(clubId = "#clubId", permission = "club:member")
    @Operation(summary = "讨论历史（在群成员；分页倒序）")
    public R<IPage<ChatMessageVO>> messages(@org.springframework.web.bind.annotation.PathVariable Long clubId,
                                            @org.springframework.web.bind.annotation.PathVariable Long id,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        return R.ok(chatService.history(clubId, id, SecurityUtils.getUserId(), page, size));
    }

    /** STOMP 发送：/app/chat/activity/{id}（先落库后广播 /topic/activity/{id}） */
    @MessageMapping("/chat/activity/{id}")
    @RepeatSubmit(intervalSeconds = 2)
    public void send(@DestinationVariable Long id,
                     @Payload ChatSendDTO dto,
                     Authentication principal,
                     SimpMessageHeaderAccessor accessor) {
        Long userId = principal == null ? null : Long.valueOf(principal.getName());
        chatService.send(id, userId, dto == null ? null : dto.getContent());
    }
}