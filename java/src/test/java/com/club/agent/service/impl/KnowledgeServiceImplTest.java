package com.club.agent.service.impl;

import com.club.agent.config.RagClientFactory;
import com.club.agent.service.ExperienceService;
import com.club.agent.vo.ExperienceSearchVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * J4 知识检索缓存单测：key 必须含 userId（源 A 的 thinking_pattern 按人隔离，
 * 缺 userId 会导致同社团串读——代码审查 P1 回归）；降级态不缓存边界。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceImplTest {

    @Mock ExperienceService experienceService;
    @Mock RagClientFactory ragClientFactory;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    KnowledgeServiceImpl service;

    final Long CLUB = 100L;

    @BeforeEach
    void setUp() {
        service = new KnowledgeServiceImpl(experienceService, ragClientFactory, redisTemplate, new ObjectMapper());
        // 单测无 Spring 上下文：rag 关闭（不触 rag）、缓存开启
        ReflectionTestUtils.setField(service, "ragEnabled", false);
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 300L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(experienceService.experience(any(), any(), anyString())).thenReturn(new ExperienceSearchVO());
    }

    @Test
    @DisplayName("缓存 key 含 userId：同问句不同用户 key 不同（thinking_pattern 串读回归）")
    void cacheKey_variesByUser() {
        service.knowledge(CLUB, 1L, "骑行建议", 8);
        service.knowledge(CLUB, 2L, "骑行建议", 8);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOps, times(2)).set(keys.capture(), anyString(), eq(Duration.ofSeconds(300L)));
        assertThat(keys.getAllValues().get(0)).contains(":1:").isNotEqualTo(keys.getAllValues().get(1));
        assertThat(keys.getAllValues().get(1)).contains(":2:");
    }

    @Test
    @DisplayName("缓存命中：同用户同问句第二次不再走 SQL 源")
    void cacheHit_skipsSqlSource() {
        when(valueOps.get(anyString()))
                .thenReturn(null)
                .thenReturn("{\"sqlItems\":[],\"fileItems\":[],\"similarActivityCount\":0}");

        service.knowledge(CLUB, 1L, "q", 8);
        service.knowledge(CLUB, 1L, "q", 8);

        verify(experienceService, times(1)).experience(any(), any(), anyString());
    }
}
