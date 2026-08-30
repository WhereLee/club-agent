package com.club.agent.security;

import com.club.agent.service.impl.UserDetailsServiceImpl;
import com.club.agent.util.JwtUtils;
import com.club.agent.util.RedisKeys;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器：解析 Authorization: Bearer token。
 * - token 合法且未进黑名单 → 加载用户写入 SecurityContext
 * - 无效/过期/黑名单 → 不设置认证（后续由 Security 链返回 401）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            Claims claims = jwtUtils.parseToken(token);
            if (claims != null && claims.getId() != null && !inBlacklist(claims.getId())) {
                try {
                    LoginUser loginUser = userDetailsService.loadUserByUsername(claims.getSubject());
                    if (loginUser.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        loginUser, null, loginUser.getAuthorities());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    // 用户已删除/禁用：不认证，走 401
                    log.warn("JWT 用户加载失败（不认证）: subject={}, err={}", claims.getSubject(), e.getMessage());
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 黑名单检查：Redis 抖动/异常时保守拒绝（无法确认 token 是否已登出，宁可 401 不误放行），并留日志便于定位 */
    private boolean inBlacklist(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.TOKEN_BLACKLIST + jti));
        } catch (Exception e) {
            log.warn("JWT 黑名单检查失败（保守拒绝）: jti={}, err={}", jti, e.getMessage());
            return true;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
