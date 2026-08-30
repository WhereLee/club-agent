package com.club.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.entity.Activity;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.service.ActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 留痕截止自动收口调度（独立类，不实现接口 → CGLIB 代理，@Scheduled 可识别）：
 * record_deadline 已过期且仍停留在留痕中(7)的活动，自动调 closeRecords 进总结(8)；
 * 与发起人手动关闭共用同一入口（system=true），CAS 兜底并发双触发只有一个成功。
 * 修复 B1：此前注释承诺"超时自动进总结"但全仓无扫描器，截止后活动永远停在状态 7。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordDeadlineScheduler {

    private final ActivityService activityService;
    private final ActivityMapper activityMapper;

    @Scheduled(fixedDelay = 60_000)
    public void scanExpired() {
        List<Activity> expired = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, Activity.STATUS_RECORDING)
                .isNotNull(Activity::getRecordDeadline)
                .lt(Activity::getRecordDeadline, LocalDateTime.now()));
        for (Activity a : expired) {
            try {
                log.info("留痕截止自动收口: activity={}, deadline={}", a.getId(), a.getRecordDeadline());
                // system=true：跳过发起人校验，操作人记系统；单条失败不影响其余
                activityService.closeRecords(a.getClubId(), a.getId(), null, true);
            } catch (Exception e) {
                log.warn("留痕截止自动收口失败（单条不影响其余）: activity={}, err={}",
                        a.getId(), e.getMessage());
            }
        }
    }
}
