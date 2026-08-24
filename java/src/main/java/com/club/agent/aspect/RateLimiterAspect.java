package com.club.agent.aspect;

import com.club.agent.annotation.RateLimiter;
import com.club.agent.exception.BizException;
import com.club.agent.util.IpUtils;
import com.club.agent.util.RedisKeys;
import com.club.agent.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 接口限流切面：Redis 计数窗口，Lua 脚本保证 INCR+EXPIRE 原子性。
 * key = 方法签名 + 用户ID（已登录）/ IP（匿名）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    /** INCR + 首次设置 TTL，原子完成 */
    private static final String LUA_INCR_EXPIRE =
            "local c = redis.call('INCR', KEYS[1]) " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return c";

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimiter)")
    public Object around(ProceedingJoinPoint pjp, RateLimiter rateLimiter) throws Throwable {
        String key = RedisKeys.RATE_LIMIT + buildKey(pjp);
        Long count = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_INCR_EXPIRE, Long.class),
                List.of(key),
                String.valueOf(rateLimiter.windowSeconds()));
        if (count != null && count > rateLimiter.limit()) {
            throw new BizException(429, "请求过于频繁，请稍后再试");
        }
        return pjp.proceed();
    }

    private String buildKey(ProceedingJoinPoint pjp) {
        String method = pjp.getSignature().toShortString();
        Long userId = SecurityUtils.getUserId();
        if (userId != null) {
            return method + ":" + userId;
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String ip = attrs != null ? IpUtils.getIp(attrs.getRequest()) : "unknown";
        return method + ":" + ip;
    }
}
