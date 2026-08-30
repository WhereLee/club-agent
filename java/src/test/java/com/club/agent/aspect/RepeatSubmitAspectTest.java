package com.club.agent.aspect;

import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RepeatSubmitAspect 真防抖行为（C1 回归）：
 * 成功不删标记（靠 TTL 过期拦截间隔内重复）、失败立即释放标记（可马上重试）、间隔内直接拒绝。
 * 纯 Mockito：mock StringRedisTemplate + ProceedingJoinPoint。
 */
class RepeatSubmitAspectTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RepeatSubmitAspect aspect = new RepeatSubmitAspect(redis);
    private final RepeatSubmit anno = mock(RepeatSubmit.class);

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> stubOps() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        return ops;
    }

    @Test
    @DisplayName("成功执行后不删标记：间隔内重复请求被 SETNX 拦截（真防抖）")
    void success_keeps_marker() throws Throwable {
        ValueOperations<String, String> ops = stubOps();
        when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(anno.intervalSeconds()).thenReturn(5L);
        ProceedingJoinPoint pjp = mockJoinPoint();

        aspect.around(pjp, anno);

        // 成功路径不得释放标记——TTL 自然过期期间重复请求直接被 setIfAbsent=false 拒绝
        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("间隔内重复请求：直接拒绝（请勿重复提交）")
    void duplicate_rejected() throws Throwable {
        stubOps();
        when(redis.opsForValue().setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);
        when(anno.intervalSeconds()).thenReturn(5L);

        assertThatThrownBy(() -> aspect.around(mockJoinPoint(), anno))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请勿重复提交");
    }

    @Test
    @DisplayName("失败路径立即释放标记：LLM/IO 失败后可马上重试")
    void failure_releases_marker() throws Throwable {
        stubOps();
        when(redis.opsForValue().setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(anno.intervalSeconds()).thenReturn(5L);
        ProceedingJoinPoint pjp = mockJoinPoint();
        when(pjp.proceed()).thenThrow(new IllegalStateException("LLM 调用失败"));

        assertThatThrownBy(() -> aspect.around(pjp, anno)).isInstanceOf(IllegalStateException.class);

        verify(redis).delete(anyString());
    }

    private ProceedingJoinPoint mockJoinPoint() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.toShortString()).thenReturn("test.RepeatSubmitAspectTest.around()");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenReturn(null);
        return pjp;
    }
}
