package com.club.agent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 讨论群 STOMP 配置（块 C）：
 * - 端点 /ws（原生 WebSocket，不走 SockJS；握手鉴权由 AuthChannelInterceptor 在 CONNECT 帧完成）
 * - Broker 前缀 /topic（广播），应用前缀 /app（@MessageMapping）
 * - 入站通道拦截：CONNECT 帧 JWT 鉴权 + SUBSCRIBE 帧订阅鉴权
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthChannelInterceptor authChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic：讨论广播；/queue：错误回执（StompExceptionAdvice 的 /user/queue/errors 解析后落在 /queue 前缀）
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}