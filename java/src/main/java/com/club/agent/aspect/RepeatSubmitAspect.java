package com.club.agent.aspect;

import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.exception.BizException;
import com.club.agent.util.IpUtils;
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
import java.util.Arrays;

/**
 * 防重复提交切面：Redis SETNX 幂等标记（真防抖，C1 契约对齐）。
 * 成功：标记靠 TTL 自然过期，间隔内重复请求直接拒绝；失败：立即释放标记，允许马上重试（LLM/IO 类接口重试体验）。
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
        } catch (Throwable e) {
            // 失败路径立即释放标记：失败可马上重试，防抖只对成功生效
            try {
                redisTemplate.delete(key);
            } catch (Exception ignored) {
                // Redis 恰好故障时不能覆盖原始业务异常（用户应看到"验证码错误"而非"Redis 故障"）
            }
            throw e;
        }
    }

    private String buildKey(ProceedingJoinPoint pjp) {
        String method = pjp.getSignature().toShortString();
        Long userId = SecurityUtils.getUserId();
        String principal = userId != null ? String.valueOf(userId) : "anon";
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String ip = attrs != null ? IpUtils.getIp(attrs.getRequest()) : "unknown";
        // 方法参数参与 key：同一接口不同目标互不干扰（审批 A 不拦审批 B），同一目标双击才互斥
        return method + ":" + principal + ":" + ip + ":" + Arrays.toString(pjp.getArgs());
    }
}
