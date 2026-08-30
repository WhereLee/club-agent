package com.club.agent.config;

import com.club.agent.common.R;
import com.club.agent.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * 安全配置：
 * - 白名单：健康检查 / 文档 / 认证公开接口 / 本地上传资源
 * - 其余请求经 JwtAuthenticationFilter 认证
 * - 无状态（STATELESS）+ CSRF 关闭（JWT 场景标准）
 * - 未认证 401 / 无权限 403 统一返回 R JSON（与全局异常风格一致）
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 匿名可访问的公开路径 */
    private static final String[] WHITE_LIST = {
            "/health", "/actuator/**",
            "/doc.html", "/webjars/**", "/v3/api-docs/**",
            "/swagger-ui/**", "/swagger-resources/**", "/favicon.ico",
            "/auth/captcha", "/auth/register", "/auth/login",
            "/uploads/**",
            "/ws/**"   // 块 C：STOMP 握手（应用层 ChannelInterceptor 鉴权，不走 HTTP 过滤器链）
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJson(response, R.fail(401, "未登录或登录已过期")))
                        .accessDeniedHandler((request, response, ex) ->
                                writeJson(response, R.fail(403, "无权限访问")))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 与全局异常同构的 JSON 输出（HTTP 200 + 业务码，前端统一按 R.code 处理） */
    private void writeJson(HttpServletResponse response, R<Void> body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
