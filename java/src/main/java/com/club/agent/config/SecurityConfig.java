package com.club.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置（壳子阶段的最小版）：
 * - 白名单放行：健康检查、actuator、knife4j 文档（探活与 API 文档必须可匿名访问）
 * - 其余请求默认要求认证（当前无认证实现，返回 401——子块 5 接入 JWT 认证链后闭合）
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 健康检查与监控（部署探活）
                        .requestMatchers("/health", "/actuator/**").permitAll()
                        // knife4j / springdoc 文档
                        .requestMatchers(
                                "/doc.html", "/webjars/**",
                                "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-resources/**", "/favicon.ico"
                        ).permitAll()
                        // 其余默认认证（子块 5 接入 JWT）
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
