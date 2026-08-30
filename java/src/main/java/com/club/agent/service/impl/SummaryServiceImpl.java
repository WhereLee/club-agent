package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.entity.ExperienceEntry;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.mapper.ExperienceEntryMapper;
import com.club.agent.service.SummaryAggregateService;
import com.club.agent.service.SummaryService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.vo.SummaryVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 活动总结服务实现（活动后阶段）：
 * - 进入总结中(8) 由 ActivityService 自动触发（@Async，不阻塞状态推进）；失败定时重试 + 手动重生成
 * - 调 Python 总结 Agent（子图）：输入聚合数据 → 输出 {report, lessons, questions}
 * - 回问闭环：awaiting 状态存 questions → 发起人回答 → resume 恢复生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryServiceImpl implements SummaryService {

    private final ActivityMapper activityMapper;
    private final ActivitySummaryMapper summaryMapper;
    private final ExperienceEntryMapper experienceEntryMapper;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final SummaryAggregateService aggregateService;

    @Value("${ai.draft.base-url:http://127.0.0.1:8094}")
    private String aiBaseUrl;

    @Value("${ai.draft.timeout-seconds:120}")
    private int aiTimeoutSeconds;

    private volatile RestClient pythonClient;

    @Async("logExecutor")
    @Override
    public void generate(Long clubId, Long activityId, Long userId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            log.warn("总结生成跳过：活动不存在或归属不符 activity={}", activityId);
            return;
        }
        // 仅总结中(8)/已归档(9) 可生成；归档后允许重生成覆盖（不动状态）
        if (a.getStatus() != Activity.STATUS_SUMMARIZING && a.getStatus() != Activity.STATUS_ARCHIVED) {
            log.warn("总结生成跳过：活动状态不允许 status={} activity={}", a.getStatus(), activityId);
            return;
        }
        ActivitySummary s = summaryMapper.selectOne(new LambdaQueryWrapper<ActivitySummary>()
                .eq(ActivitySummary::getActivityId, activityId));
        // 手动重生成：存在未处理的待确认问题先回答（1054）
        if (s != null && ActivitySummary.STATUS_AWAITING.equals(s.getStatus()) && userId != null) {
            throw new BizException(ResultCode.BIZ_SUMMARY_QUESTIONS_PENDING);
        }
        // upsert pending（重试计数递增）；已存在行必须 update（insert 会主键冲突）
        boolean exists = s != null;
        if (s == null) {
            s = new ActivitySummary();
            s.setId(IdWorker.getId());
            s.setActivityId(activityId);
            s.setRetryCount(0);
        }
        s.setStatus(ActivitySummary.STATUS_PENDING);
        s.setRetryCount(s.getRetryCount() == null ? 1 : s.getRetryCount() + 1);
        s.setGeneratedBy(userId);
        s.setUpdatedAt(LocalDateTime.now());
        if (exists) {
            summaryMapper.updateById(s);
        } else {
            summaryMapper.insert(s);
        }
        // 调 Python 总结 Agent（子图）；慢调用放事务外，异常走 failed + 重试
        try {
            Map<String, Object> input = aggregateInput(clubId, activityId);
            Map<String, Object> resp = python().post()
                    .uri("/agent/summarize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "activity_id", String.valueOf(activityId),
                            "input", input))
                    .retrieve().body(Map.class);
            if (resp == null) {
                throw new BizException("总结 Agent 无响应");
            }
            String status = String.valueOf(resp.getOrDefault("status", ""));
            if (ActivitySummary.STATUS_AWAITING.equals(status)) {
                // 子图自主决策：需要发起人补充信息
                s.setStatus(ActivitySummary.STATUS_AWAITING);
                s.setQuestions(json(resp.get("questions")));
            } else {
                Object report = resp.get("report");
                if (report == null) {
                    throw new BizException("总结 Agent 未返回报告");
                }
                s.setStatus(ActivitySummary.STATUS_SUCCESS);
                s.setReport(json(report));
                s.setGeneratedAt(LocalDateTime.now());
                saveLessons(activityId, resp.get("lessons"));
            }
        } catch (Exception e) {
            s.setStatus(ActivitySummary.STATUS_FAILED);
            log.warn("活动总结生成失败（待重试）: activity={}, err={}", activityId, e.getMessage());
        }
        s.setUpdatedAt(LocalDateTime.now());
        summaryMapper.updateById(s);
    }

    @Override
    public void resume(Long clubId, Long activityId, Long userId, Map<String, String> answers) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        if (!a.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        ActivitySummary s = summaryMapper.selectOne(new LambdaQueryWrapper<ActivitySummary>()
                .eq(ActivitySummary::getActivityId, activityId));
        if (s == null || !ActivitySummary.STATUS_AWAITING.equals(s.getStatus())) {
            throw new BizException(ResultCode.BIZ_SUMMARY_NOT_GENERATED);
        }
        try {
            Map<String, Object> resp = python().post()
                    .uri("/agent/summarize/resume")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("activity_id", String.valueOf(activityId), "answers", answers))
                    .retrieve().body(Map.class);
            if (resp == null || resp.get("report") == null) {
                throw new BizException("总结恢复生成无响应");
            }
            s.setAnswers(json(answers));
            s.setStatus(ActivitySummary.STATUS_SUCCESS);
            s.setReport(json(resp.get("report")));
            s.setGeneratedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            summaryMapper.updateById(s);
            saveLessons(activityId, resp.get("lessons"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("总结恢复生成失败: activity={}, err={}", activityId, e.getMessage());
            throw new BizException(ResultCode.FAIL.getCode(), "总结生成失败，请稍后重试");
        }
    }

    @Override
    public SummaryVO detail(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        ActivitySummary s = summaryMapper.selectOne(new LambdaQueryWrapper<ActivitySummary>()
                .eq(ActivitySummary::getActivityId, activityId));
        if (s == null) {
            throw new BizException(ResultCode.BIZ_SUMMARY_NOT_GENERATED);
        }
        SummaryVO vo = new SummaryVO();
        vo.setActivityId(activityId);
        vo.setStatus(s.getStatus());
        vo.setRetryCount(s.getRetryCount());
        vo.setGeneratedAt(s.getGeneratedAt());
        vo.setLessons(lessons(activityId));
        try {
            if (StringUtils.hasText(s.getReport())) {
                Map<String, Object> report = objectMapper.readValue(s.getReport(), new TypeReference<>() {
                });
                vo.setMetrics((Map<String, Object>) report.getOrDefault("metrics", Map.of()));
                vo.setReportText(String.valueOf(report.getOrDefault("report_text", "")));
            }
            if (StringUtils.hasText(s.getQuestions())) {
                vo.setQuestions(objectMapper.readValue(s.getQuestions(), new TypeReference<>() {
                }));
            }
            if (StringUtils.hasText(s.getAnswers())) {
                vo.setAnswers(objectMapper.readValue(s.getAnswers(), new TypeReference<>() {
                }));
            }
        } catch (Exception e) {
            log.warn("总结详情 JSON 解析失败: activity={}", activityId, e);
        }
        return vo;
    }

    /** 本活动沉淀的经验条目（来源可追溯，供管理层视图） */
    private List<Map<String, Object>> lessons(Long activityId) {
        List<ExperienceEntry> list = experienceEntryMapper.selectList(new LambdaQueryWrapper<ExperienceEntry>()
                .eq(ExperienceEntry::getActivityId, activityId));
        return list.stream().map(e -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("category", e.getCategory());
            m.put("title", e.getTitle());
            m.put("content", e.getContent());
            return m;
        }).toList();
    }

    /** 定时重试 failed 由 SummaryScheduler 独立调度（@Scheduled 方法不进接口，避免 JDK 代理限制） */

    /** 聚合输入（I2：Java 结构化指标，确定性强；Python 只做文字总结与经验提炼） */
    private Map<String, Object> aggregateInput(Long clubId, Long activityId) {
        return aggregateService.aggregate(clubId, activityId);
    }

    /** 经验条目落库（统一经验库 experience_entry，来源活动可追溯） */
    @SuppressWarnings("unchecked")
    private void saveLessons(Long activityId, Object lessons) {
        if (!(lessons instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) raw;
            ExperienceEntry e = new ExperienceEntry();
            e.setId(IdWorker.getId());
            e.setActivityId(activityId);
            e.setCategory(String.valueOf(m.getOrDefault("category", ExperienceEntry.CATEGORY_LESSON)));
            e.setTitle(String.valueOf(m.getOrDefault("title", "")));
            e.setContent(String.valueOf(m.getOrDefault("content", "")));
            Object metrics = m.get("metrics");
            if (metrics != null) {
                e.setMetrics(json(metrics));
            }
            e.setStatus(ExperienceEntry.STATUS_VALID);
            experienceEntryMapper.insert(e);
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException(ResultCode.FAIL);
        }
    }

    private RestClient python() {
        if (pythonClient == null) {
            synchronized (this) {
                if (pythonClient == null) {
                    SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
                    rf.setConnectTimeout(Duration.ofSeconds(5));
                    rf.setReadTimeout(Duration.ofSeconds(aiTimeoutSeconds));
                    pythonClient = restClientBuilder.baseUrl(aiBaseUrl).requestFactory(rf).build();
                }
            }
        }
        return pythonClient;
    }
}
