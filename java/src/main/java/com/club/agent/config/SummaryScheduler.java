package com.club.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.service.SummaryRagSyncService;
import com.club.agent.service.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动总结失败重试调度（独立类，不实现接口 → CGLIB 代理，@Scheduled 可识别）：
 * failed 且未达上限的总结每分钟重试；自动生成失败不阻塞，手动重生成始终可用。
 * rag 同步补偿：success 且 rag_file_id 为空的已归档总结每 5 分钟重推（rag 故障窗口恢复后自愈）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryScheduler {

    private final SummaryService summaryService;
    private final ActivitySummaryMapper summaryMapper;
    private final ActivityMapper activityMapper;
    private final SummaryRagSyncService summaryRagSync;

    @Scheduled(fixedDelay = 60_000)
    public void retryFailed() {
        List<ActivitySummary> failed = summaryMapper.selectList(new LambdaQueryWrapper<ActivitySummary>()
                .eq(ActivitySummary::getStatus, ActivitySummary.STATUS_FAILED)
                .lt(ActivitySummary::getRetryCount, ActivitySummary.MAX_RETRY));
        for (ActivitySummary s : failed) {
            Activity a = activityMapper.selectById(s.getActivityId());
            if (a == null) {
                continue;
            }
            try {
                log.info("总结失败定时重试: activity={}, retry={}", s.getActivityId(), s.getRetryCount());
                summaryService.generate(a.getClubId(), s.getActivityId(), null);
            } catch (Exception e) {
                // C3：aiExecutor 满拒等提交异常——单条失败不影响其余，留给下轮扫描
                log.warn("总结重试提交失败（单条不影响其余）: activity={}, err={}",
                        s.getActivityId(), e.getMessage());
            }
        }
    }

    /** rag 同步补偿：success + 未入库 + 已归档，5 分钟后重推（失败置空 ragFileId 后由本方法接管） */
    @Scheduled(fixedDelay = 300_000)
    public void ragSyncRetry() {
        List<ActivitySummary> pending = summaryMapper.selectList(new LambdaQueryWrapper<ActivitySummary>()
                .eq(ActivitySummary::getStatus, ActivitySummary.STATUS_SUCCESS)
                .isNull(ActivitySummary::getRagFileId)
                .lt(ActivitySummary::getUpdatedAt, LocalDateTime.now().minusMinutes(5)));
        for (ActivitySummary s : pending) {
            Activity a = activityMapper.selectById(s.getActivityId());
            if (a == null || a.getStatus() != Activity.STATUS_ARCHIVED) {
                continue;  // 仅已归档活动需要入知识库（总结中 8 态尚未定稿）
            }
            try {
                log.info("总结报告 rag 同步补偿重推: activity={}", s.getActivityId());
                summaryRagSync.syncToRag(a.getClubId(), s.getActivityId());
            } catch (Exception e) {
                // 池满提交被拒等异常：单条失败不影响其余，留给下轮扫描
                log.warn("rag 同步补偿提交失败（留给下轮）: activity={}, err={}", s.getActivityId(), e.getMessage());
            }
        }
    }
}
