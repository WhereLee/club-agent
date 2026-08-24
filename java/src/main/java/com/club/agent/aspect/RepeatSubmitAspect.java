package com.club.agent.aspect;

import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.exception.BizException;
import com.club.agent.util.RedisKeys;
import com.club.agent.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 防重复提交切面：Redis SETNX 幂等标记。
 * 执行完成后释放标记（防抖语义），间隔内重复请求直接拒绝。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RepeatSubmitAspect {

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint pjp, RepeatSubmit repeatSubmit) throws Throwable {
        String key = RedisKeys.REPEAT_SUBMIT + buildKey(pjp);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(repeatSubmit.intervalSeconds()));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BizException("请勿重复提交");
        }
        try {
            return pjp.proceed();
        } finally {
            redisTemplate.delete(key);
        }
    }

    private String buildKey(ProceedingJoinPoint pjp) {
        String method = pjp.getSignature().toShortString();
        Long userId = SecurityUtils.getUserId();
        if (userId != null) {
            return method + ":" + userId;
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return method + ":" + (attrs != null ? attrs.getSessionId() : "anon");
    }
}
