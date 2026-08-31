package com.club.agent.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.service.SummaryRagSyncService;
import com.club.agent.service.SummaryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 总结调度器单测（P2-2 补偿闭环）：rag 同步补偿——仅已归档 + 未入库的总结被重推；
 * 单条提交被拒不影响其余；未归档活动的缺失 ragFileId 不重推。
 */
@ExtendWith(MockitoExtension.class)
class SummarySchedulerTest {

    @Mock SummaryService summaryService;
    @Mock ActivitySummaryMapper summaryMapper;
    @Mock ActivityMapper activityMapper;
    @Mock SummaryRagSyncService summaryRagSync;

    @InjectMocks SummaryScheduler scheduler;

    final Long ACT_ARCHIVED = 200L;
    final Long ACT_SUMMARIZING = 201L;
    final Long CLUB = 100L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(Activity.class, ActivitySummary.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    private ActivitySummary summary(Long activityId) {
        ActivitySummary s = new ActivitySummary();
        s.setId(activityId);
        s.setActivityId(activityId);
        s.setStatus(ActivitySummary.STATUS_SUCCESS);
        s.setRagFileId(null);
        s.setUpdatedAt(LocalDateTime.now().minusHours(1));
        return s;
    }

    private Activity activity(Long id, int status) {
        Activity a = new Activity();
        a.setId(id);
        a.setClubId(CLUB);
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("补偿扫描：success + ragFileId 为空，仅已归档活动触发重推")
    void ragSyncRetry_onlyArchived() {
        when(summaryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(summary(ACT_ARCHIVED), summary(ACT_SUMMARIZING)));
        when(activityMapper.selectById(ACT_ARCHIVED)).thenReturn(activity(ACT_ARCHIVED, Activity.STATUS_ARCHIVED));
        when(activityMapper.selectById(ACT_SUMMARIZING)).thenReturn(activity(ACT_SUMMARIZING, Activity.STATUS_SUMMARIZING));

        scheduler.ragSyncRetry();

        verify(summaryRagSync).syncToRag(CLUB, ACT_ARCHIVED);
        verify(summaryRagSync, never()).syncToRag(anyLong(), org.mockito.ArgumentMatchers.eq(ACT_SUMMARIZING));
    }

    @Test
    @DisplayName("补偿扫描：单条提交被拒（池满）不影响其余，留给下轮")
    void ragSyncRetry_oneRejected_othersProceed() {
        when(summaryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(summary(ACT_ARCHIVED), summary(ACT_SUMMARIZING)));
        when(activityMapper.selectById(ACT_ARCHIVED)).thenReturn(activity(ACT_ARCHIVED, Activity.STATUS_ARCHIVED));
        when(activityMapper.selectById(ACT_SUMMARIZING)).thenReturn(activity(ACT_SUMMARIZING, Activity.STATUS_ARCHIVED));
        doThrow(new RuntimeException("TaskRejectedException（池满）")).when(summaryRagSync).syncToRag(CLUB, ACT_ARCHIVED);

        scheduler.ragSyncRetry();  // 不抛异常

        verify(summaryRagSync).syncToRag(CLUB, ACT_ARCHIVED);
        verify(summaryRagSync).syncToRag(CLUB, ACT_SUMMARIZING);
        verify(summaryRagSync, times(2)).syncToRag(anyLong(), anyLong());
    }

    @Test
    @DisplayName("补偿扫描：活动已软删/不存在时跳过")
    void ragSyncRetry_activityMissing_skips() {
        when(summaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(summary(ACT_ARCHIVED)));
        when(activityMapper.selectById(ACT_ARCHIVED)).thenReturn(null);

        scheduler.ragSyncRetry();

        verify(summaryRagSync, never()).syncToRag(anyLong(), anyLong());
    }
}
