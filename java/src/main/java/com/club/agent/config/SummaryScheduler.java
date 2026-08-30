package com.club.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.service.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 活动总结失败重试调度（独立类，不实现接口 → CGLIB 代理，@Scheduled 可识别）：
 * failed 且未达上限的总结每分钟重试；自动生成失败不阻塞，手动重生成始终可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryScheduler {

    private final SummaryService summaryService;
    private final ActivitySummaryMapper summaryMapper;
    private final ActivityMapper activityMapper;

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
}
