package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.config.RagClientFactory;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.entity.ExperienceEntry;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.mapper.ExperienceEntryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * J1 总结报告入 rag 单测：状态门 / 首次入库 / 替换语义（软删旧+重推）/ 渲染内容。
 * 注：单测直接调 syncToRag（无 Spring 代理，@Async 不生效，同步执行便于断言）。
 */
@ExtendWith(MockitoExtension.class)
class SummaryRagSyncServiceImplTest {

    @Mock ActivityMapper activityMapper;
    @Mock ActivitySummaryMapper summaryMapper;
    @Mock ExperienceEntryMapper experienceEntryMapper;
    @Mock RagClientFactory ragClientFactory;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks SummaryRagSyncServiceImpl syncService;

    final Long CLUB = 100L;
    final Long ACT = 200L;

    @BeforeEach
    void setUp() {
        // 单测无 Spring 上下文：@Value 不生效，手动开启开关 + 注入真实 ObjectMapper
        ReflectionTestUtils.setField(syncService, "ragEnabled", true);
        ReflectionTestUtils.setField(syncService, "objectMapper", new ObjectMapper());
        // 并发单飞锁默认可获取（锁冲突/纯渲染用例单独覆盖；lenient：部分用例不走锁）
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(Activity.class, ActivitySummary.class, ExperienceEntry.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    private Activity activity() {
        Activity a = new Activity();
        a.setId(ACT);
        a.setClubId(CLUB);
        a.setStatus(Activity.STATUS_ARCHIVED);
        a.setPlannedTime("2026-09-01 14:00");
        a.setPlannedLocation("操场");
        return a;
    }

    private ActivitySummary summary(String status, Long ragFileId) {
        ActivitySummary s = new ActivitySummary();
        s.setId(1L);
        s.setActivityId(ACT);
        s.setStatus(status);
        s.setRagFileId(ragFileId);
        s.setReport("{\"metrics\":{\"signup\":{\"total\":12}},\"report_text\":\"本次活动顺利完成\"}");
        return s;
    }

    @Test
    @DisplayName("状态门：总结非 success 不推 rag")
    void skips_whenNotSuccess() {
        when(summaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(summary("pending", null));

        syncService.syncToRag(CLUB, ACT);

        verify(ragClientFactory, never()).ingestBytes(any(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("首次入库：无旧文件不软删，ingest 后回填 ragFileId")
    void firstSync_ingestsAndBackfills() {
        when(summaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(summary("success", null));
        when(activityMapper.selectById(ACT)).thenReturn(activity());
        lenient().when(experienceEntryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(ragClientFactory.ingestBytes(any(byte[].class), anyString(), eq(CLUB), eq("summary"))).thenReturn(777L);

        syncService.syncToRag(CLUB, ACT);

        verify(ragClientFactory, never()).deactivateFile(anyLong(), anyLong());
        verify(summaryMapper).updateById(org.mockito.ArgumentMatchers.argThat(
                (ActivitySummary s) -> Long.valueOf(777L).equals(s.getRagFileId())));
    }

    @Test
    @DisplayName("替换语义：存在旧文件先软删再重推")
    void replace_deactivatesOldBeforeIngest() {
        when(summaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(summary("success", 99L));
        when(activityMapper.selectById(ACT)).thenReturn(activity());
        lenient().when(experienceEntryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(ragClientFactory.ingestBytes(any(byte[].class), anyString(), eq(CLUB), eq("summary"))).thenReturn(888L);

        syncService.syncToRag(CLUB, ACT);

        verify(ragClientFactory).deactivateFile(99L, CLUB);
        verify(ragClientFactory).ingestBytes(any(byte[].class), anyString(), eq(CLUB), eq("summary"));
    }

    @Test
    @DisplayName("自愈标记：ingest 失败时 ragFileId 置空（旧文件已软删，下次触发走全新推送）")
    void ingestFails_clearsRagFileId() {
        when(summaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(summary("success", 99L));
        when(activityMapper.selectById(ACT)).thenReturn(activity());
        lenient().when(experienceEntryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(ragClientFactory.ingestBytes(any(byte[].class), anyString(), eq(CLUB), eq("summary")))
                .thenThrow(new RuntimeException("rag down"));

        syncService.syncToRag(CLUB, ACT);  // 吞异常不抛

        verify(ragClientFactory).deactivateFile(99L, CLUB);
        ArgumentCaptor<ActivitySummary> cap = ArgumentCaptor.forClass(ActivitySummary.class);
        verify(summaryMapper).updateById(cap.capture());
        assertThat(cap.getValue().getRagFileId()).isNull();
    }

    @Test
    @DisplayName("并发单飞：锁被占用（他处入库进行中）时跳过，不软删不重推")
    void lockConflict_skipsIngest() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        syncService.syncToRag(CLUB, ACT);

        verify(summaryMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(ragClientFactory, never()).deactivateFile(anyLong(), anyLong());
        verify(ragClientFactory, never()).ingestBytes(any(byte[].class), anyString(), anyLong(), anyString());
        verify(redisTemplate, never()).delete(anyString());  // 未持有锁不释放
    }

    @Test
    @DisplayName("渲染内容：指标平铺 + 正文 + 沉淀经验 + 文件名净化")
    void renderMarkdown_containsAllSections() {
        Activity a = activity();
        ActivitySummary s = summary("success", null);
        ExperienceEntry le = new ExperienceEntry();
        le.setTitle("签到要提前半小时");
        le.setContent("迟到集中在开场前 10 分钟");

        String md = syncService.renderMarkdown(a, s, List.of(le));

        assertThat(md).contains("# 2026-09-01 14:00 操场 活动总结报告")
                .contains("signup.total：12")
                .contains("本次活动顺利完成")
                .contains("### 签到要提前半小时")
                .contains("迟到集中在开场前 10 分钟");
        // 文件名中冒号被净化为下划线（Windows 文件名非法字符）
        assertThat(SummaryRagSyncServiceImpl.filename(a)).isEqualTo("活动总结-2026-09-01 14_00 操场.md");
    }

    @Test
    @DisplayName("锁释放：正常路径完成后删除锁 key（key 含 activityId）")
    void lock_releasedAfterSync() {
        when(summaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(summary("pending", null));

        syncService.syncToRag(CLUB, ACT);

        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.argThat((String k) -> k.contains(ACT.toString())));
    }
}
