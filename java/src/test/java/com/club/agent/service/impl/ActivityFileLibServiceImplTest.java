package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.config.RagClientFactory;
import com.club.agent.entity.ActivityFileLib;
import com.club.agent.mapper.ActivityFileLibMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ActivityOwnership;
import com.club.agent.storage.StorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资料库懒同步节流单测：parsing 记录 30s 窗口内重复访问不查 rag（N+1 外部调用降频）。
 */
@ExtendWith(MockitoExtension.class)
class ActivityFileLibServiceImplTest {

    @Mock ActivityFileLibMapper fileLibMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock StorageService storageService;
    @Mock RagClientFactory ragClientFactory;
    @Mock ActivityOwnership ownership;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks ActivityFileLibServiceImpl fileLibService;

    final Long CLUB = 100L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ActivityFileLib.class);
    }

    @BeforeEach
    void setUp() {
        // lenient：非 parsing 用例不走节流不调 opsForValue
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private ActivityFileLib lib(String ragStatus, Long ragFileId) {
        ActivityFileLib r = new ActivityFileLib();
        r.setId(1L);
        r.setClubId(CLUB);
        r.setRagStatus(ragStatus);
        r.setRagFileId(ragFileId);
        r.setStatus(ActivityFileLib.STATUS_VALID);
        return r;
    }

    @Test
    @DisplayName("节流命中：窗口内重复访问（setIfAbsent=false）不查 rag、不改状态")
    void throttled_skipsRagCall() {
        when(fileLibMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lib(ActivityFileLib.RAG_PARSING, 9L)));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        lenient().when(sysUserMapper.selectById(anyLong())).thenReturn(null);

        fileLibService.list(CLUB);

        verify(ragClientFactory, never()).queryParseStatus(anyLong(), anyLong());
        verify(fileLibMapper, never()).updateById(any(ActivityFileLib.class));
    }

    @Test
    @DisplayName("正常同步：拿到节流令牌时查 rag 并回填终态")
    void synced_backfillsStatus() {
        when(fileLibMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lib(ActivityFileLib.RAG_PARSING, 9L)));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(ragClientFactory.queryParseStatus(9L, CLUB)).thenReturn(ActivityFileLib.RAG_SUCCESS);
        lenient().when(sysUserMapper.selectById(anyLong())).thenReturn(null);

        fileLibService.list(CLUB);

        verify(ragClientFactory).queryParseStatus(9L, CLUB);
        verify(fileLibMapper).updateById(org.mockito.ArgumentMatchers.argThat(
                (ActivityFileLib r) -> ActivityFileLib.RAG_SUCCESS.equals(r.getRagStatus())));
    }

    @Test
    @DisplayName("非 parsing 记录不走节流也不查 rag")
    void nonParsing_skipsThrottle() {
        when(fileLibMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(lib(ActivityFileLib.RAG_SUCCESS, 9L), lib(ActivityFileLib.RAG_PENDING, null)));
        lenient().when(sysUserMapper.selectById(anyLong())).thenReturn(null);

        fileLibService.list(CLUB);

        verify(redisTemplate, never()).opsForValue();
        verify(ragClientFactory, never()).queryParseStatus(anyLong(), anyLong());
    }
}
