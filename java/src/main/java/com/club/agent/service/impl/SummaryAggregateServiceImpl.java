package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityAttendance;
import com.club.agent.entity.ActivityDiscussionSummary;
import com.club.agent.entity.ActivityRecordScore;
import com.club.agent.entity.ActivitySignup;
import com.club.agent.entity.ActivitySuggestion;
import com.club.agent.entity.ChatMessage;
import com.club.agent.entity.ExperienceEntry;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.common.ResultCode;
import com.club.agent.mapper.ActivityAttendanceMapper;
import com.club.agent.mapper.ActivityDiscussionSummaryMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivityRecordScoreMapper;
import com.club.agent.mapper.ActivitySignupMapper;
import com.club.agent.mapper.ActivitySuggestionMapper;
import com.club.agent.mapper.ChatMessageMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.ExperienceEntryMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.RewardService;
import com.club.agent.service.SummaryAggregateService;
import com.club.agent.vo.RewardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动总结数据聚合实现（I2）：
 * 全部指标 Java 计算（确定性强、可断言），输出结构化 Map 供 Python 总结 Agent 消费。
 * 快照语义：进入总结中(8) 后报名/签到/留痕不再变更，直接查库即快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryAggregateServiceImpl implements SummaryAggregateService {

    private static final String NOT_INTERESTED = "不感兴趣";

    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final ActivityAttendanceMapper attendanceMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final FormSubmissionMapper formSubmissionMapper;
    private final FormAnswerMapper formAnswerMapper;
    private final ActivityRecordScoreMapper scoreMapper;
    private final ActivitySuggestionMapper suggestionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ActivityDiscussionSummaryMapper discussionSummaryMapper;
    private final ExperienceEntryMapper experienceEntryMapper;
    private final SysUserMapper sysUserMapper;
    private final RewardService rewardService;

    @Override
    public Map<String, Object> aggregate(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activity", activity(a));
        out.put("signup", signup(activityId));
        out.put("attendance", attendance(activityId));
        out.put("record", record(activityId));
        out.put("reward", reward(clubId, activityId));
        out.put("discussion", discussion(activityId));
        out.put("experience", experience(clubId, a.getContent()));
        return out;
    }

    // ---- 活动基本信息 ----

    private Map<String, Object> activity(Activity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(a.getId()));
        m.put("content", nvl(a.getContent()));
        m.put("planned_time", nvl(a.getPlannedTime()));
        m.put("planned_location", nvl(a.getPlannedLocation()));
        SysUser u = sysUserMapper.selectById(a.getUserId());
        m.put("creator_name", u == null ? "未知" : u.getNickname());
        return m;
    }

    // ---- 报名统计 ----

    private Map<String, Object> signup(Long activityId) {
        List<ActivitySignup> list = signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId));
        int participate = 0, notParticipate = 0, onlineAssist = 0;
        for (ActivitySignup s : list) {
            if (ActivitySignup.CHOICE_PARTICIPATE.equals(s.getChoice())) {
                participate++;
            } else {
                notParticipate++;
                if (Boolean.TRUE.equals(s.getOnlineAssist())) {
                    onlineAssist++;
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", list.size());
        m.put("participate", participate);
        m.put("not_participate", notParticipate);
        m.put("online_assist", onlineAssist);
        m.put("not_interested", notInterestedCount(activityId));
        return m;
    }

    /** 问卷 system_flag=1 字段答"不感兴趣"的人数（报名拦截标记，SignupServiceImpl 同口径） */
    private int notInterestedCount(Long activityId) {
        FormTemplate survey = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_SURVEY));
        if (survey == null) {
            return 0;
        }
        FormField interest = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                        .eq(FormField::getTemplateId, survey.getId())
                        .eq(FormField::getSystemFlag, 1))
                .stream().findFirst().orElse(null);
        if (interest == null) {
            return 0;
        }
        return (int) formSubmissionMapper.selectList(new LambdaQueryWrapper<FormSubmission>()
                        .eq(FormSubmission::getTemplateId, survey.getId()))
                .stream().filter(sub -> {
                    FormAnswer ans = formAnswerMapper.selectOne(new LambdaQueryWrapper<FormAnswer>()
                            .eq(FormAnswer::getSubmissionId, sub.getId())
                            .eq(FormAnswer::getFieldId, interest.getId()));
                    return ans != null && NOT_INTERESTED.equals(ans.getValue());
                }).count();
    }

    // ---- 签到统计 ----

    private Map<String, Object> attendance(Long activityId) {
        List<ActivityAttendance> list = attendanceMapper.selectList(new LambdaQueryWrapper<ActivityAttendance>()
                .eq(ActivityAttendance::getActivityId, activityId));
        Map<String, Object> m = new LinkedHashMap<>();
        // 应到 = 报名参加人数（attendance 表仅管理层确认的到场记录）
        int expected = (int) signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getActivityId, activityId)
                        .eq(ActivitySignup::getChoice, ActivitySignup.CHOICE_PARTICIPATE))
                .stream().count();
        m.put("expected", expected);
        m.put("present", list.size());
        return m;
    }

    // ---- 留痕统计 ----

    private Map<String, Object> record(Long activityId) {
        FormTemplate t = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_RECORD));
        Map<String, Object> m = new LinkedHashMap<>();
        List<FormSubmission> subs = t == null ? List.of() : formSubmissionMapper.selectList(
                new LambdaQueryWrapper<FormSubmission>().eq(FormSubmission::getTemplateId, t.getId()));
        m.put("submitted", subs.size());
        List<ActivityRecordScore> scores = scoreMapper.selectList(new LambdaQueryWrapper<ActivityRecordScore>()
                .eq(ActivityRecordScore::getActivityId, activityId));
        double avgScore = scores.stream().mapToInt(s -> s.getScore() == null ? 0 : s.getScore()).average().orElse(0);
        double avgAi = scores.stream().filter(s -> s.getAiScore() != null)
                .mapToInt(ActivityRecordScore::getAiScore).average().orElse(0);
        m.put("avg_score", Math.round(avgScore * 10) / 10.0);
        m.put("avg_ai_score", Math.round(avgAi * 10) / 10.0);
        // 覆盖率 = 已提交 / 报名参加；未提交名单（报名参加但无留痕）
        Set<Long> participated = signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getActivityId, activityId)
                        .eq(ActivitySignup::getChoice, ActivitySignup.CHOICE_PARTICIPATE))
                .stream().map(ActivitySignup::getUserId).collect(Collectors.toSet());
        Set<Long> submittedIds = subs.stream().map(FormSubmission::getUserId).collect(Collectors.toSet());
        int expected = participated.size();
        m.put("coverage", expected == 0 ? 0 : Math.round(subs.size() * 100.0 / expected) / 100.0);
        List<String> missing = new ArrayList<>();
        for (Long uid : participated) {
            if (!submittedIds.contains(uid)) {
                SysUser u = sysUserMapper.selectById(uid);
                missing.add(u == null ? "未知" : u.getNickname());
            }
        }
        m.put("missing", missing);
        return m;
    }

    // ---- 奖励统计（复用 RewardService 同口径） ----

    private Map<String, Object> reward(Long clubId, Long activityId) {
        List<RewardVO> list = rewardService.rewards(clubId, activityId);
        Map<String, Long> levelDist = list.stream().collect(Collectors.groupingBy(
                RewardVO::getLevelName, Collectors.counting()));
        int adopted = (int) suggestionMapper.selectList(new LambdaQueryWrapper<ActivitySuggestion>()
                        .eq(ActivitySuggestion::getActivityId, activityId)
                        .eq(ActivitySuggestion::getAdopted, true))
                .stream().count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level_dist", levelDist);
        m.put("adopted_suggestions", adopted);
        m.put("top_score", list.stream().mapToInt(RewardVO::getTotalScore).max().orElse(0));
        return m;
    }

    // ---- 讨论统计 ----

    private Map<String, Object> discussion(Long activityId) {
        List<ChatMessage> msgs = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getActivityId, activityId));
        long quality = msgs.stream().filter(m -> !Boolean.TRUE.equals(m.getLowQuality())).count();
        long highFreq = discussionSummaryMapper.selectList(new LambdaQueryWrapper<ActivityDiscussionSummary>()
                        .eq(ActivityDiscussionSummary::getActivityId, activityId)
                        .eq(ActivityDiscussionSummary::getHighFreq, true))
                .stream().count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message_count", msgs.size());
        m.put("quality_rate", msgs.isEmpty() ? 0 : Math.round(quality * 100.0 / msgs.size()) / 100.0);
        m.put("high_freq_count", (int) highFreq);
        return m;
    }

    // ---- 历史经验（Java 预检索，子图 retrieve_history 直接消费；userId=null 时 thinking_pattern 自动排除） ----

    private Map<String, Object> experience(Long clubId, String topic) {
        List<ExperienceEntry> entries = experienceEntryMapper.selectForAgent(
                clubId, null, topic == null ? null : topic.substring(0, Math.min(30, topic.length())), 5);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ExperienceEntry e : entries) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("category", nvl(e.getCategory()));
            it.put("title", nvl(e.getTitle()));
            it.put("content", nvl(e.getContent()));
            items.add(it);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", items);
        m.put("water", experienceEntryMapper.countForAgent(clubId));
        return m;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
