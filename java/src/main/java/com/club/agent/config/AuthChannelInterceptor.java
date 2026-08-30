package com.club.agent.config;

import com.club.agent.service.ChatService;
import com.club.agent.util.JwtUtils;
import com.club.agent.util.RedisKeys;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 讨论群 STOMP 鉴权（块 C）：
 * - CONNECT 帧：Authorization: Bearer {JWT} → 校验签名 + Redis 未拉黑 → 设置会话用户（principal=userId）
 * - SUBSCRIBE 帧：仅允许 /topic/activity/{id} 且订阅者在入群快照中（老师/不感兴趣者收到 ERROR 帧）
 * - 其余命令放行（心跳等）
 */
@Slf4j
@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern ACTIVITY_TOPIC = Pattern.compile("^/topic/activity/(\\d+)$");

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    /**
     * @Lazy 打破循环：本拦截器 → ChatServiceImpl → SimpMessagingTemplate → WebSocketConfig → 本拦截器。
     * 订阅帧到达（运行期）时 ChatService 早已就绪，延迟代理无副作用。
     */
    private final ChatService chatService;

    public AuthChannelInterceptor(JwtUtils jwtUtils, StringRedisTemplate redisTemplate,
                                  @Lazy ChatService chatService) {
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Long userId = authenticate(accessor);
            if (userId == null) {
                throw new MessageDeliveryException("未授权：JWT 无效或已失效");
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Long userId = currentUserId(accessor);
            String dest = accessor.getDestination();
            Matcher m = dest == null ? null : ACTIVITY_TOPIC.matcher(dest);
            if (m != null && m.matches()) {
                long activityId = Long.parseLong(m.group(1));
                if (userId == null || !chatService.isMember(activityId, userId)) {
                    throw new MessageDeliveryException("无权限订阅讨论群");
                }
            } else if (dest != null && !dest.startsWith("/user/")) {
                // 非讨论主题的订阅一律拒绝（防信息泄露）
                throw new MessageDeliveryException("禁止订阅该目的地");
            }
        }
        return message;
    }

    private Long authenticate(StompHeaderAccessor accessor) {
        String token = null;
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith(BEARER_PREFIX)) {
            token = auth.substring(BEARER_PREFIX.length());
        }
        if (!StringUtils.hasText(token)) {
            return null;
        }
        Claims claims = jwtUtils.parseToken(token);
        Long userId = claims == null ? null : jwtUtils.getUserId(claims);
        if (userId == null || claims.getId() == null) {
            return null;
        }
        // 与 HTTP 过滤器同源：已登出（拉黑）的 token 拒绝
        if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.TOKEN_BLACKLIST + claims.getId()))) {
            return null;
        }
        return userId;
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            return null;
        }
        // CONNECT 时 setUser(UsernamePasswordAuthenticationToken(userId, ...)) → getName() 即 userId 字符串
        return Long.valueOf(accessor.getUser().getName());
    }
}