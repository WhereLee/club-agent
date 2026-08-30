package com.club.agent.config;

import com.club.agent.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.security.Principal;

/**
 * STOMP 消息异常统一处理（块 C）：
 * @MessageMapping 抛出的业务异常（如活动已发布只读、不在群）默认不会回 ERROR 帧，客户端将无感知；
 * 这里捕获后用 SimpMessagingTemplate 显式发送到 /user/{userId}/queue/errors（前端订阅后提示）。
 * 不用 @SendToUser 注解返回值方案——异常处理器返回值处理链不可靠，显式发送可控。
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class StompExceptionAdvice {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageExceptionHandler
    public void handle(Exception e, Principal principal, SimpMessageHeaderAccessor accessor) {
        String msg = e instanceof BizException be ? be.getMessage() : "消息发送失败，请稍后重试";
        if (!(e instanceof BizException)) {
            log.error("STOMP 消息处理异常: session={}", accessor.getSessionId(), e);
        } else {
            log.warn("STOMP 业务拒绝: {} | session={}", msg, accessor.getSessionId());
        }
        String userId = principal == null ? null : principal.getName();
        if (userId != null) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/errors", msg);
        }
    }
}
