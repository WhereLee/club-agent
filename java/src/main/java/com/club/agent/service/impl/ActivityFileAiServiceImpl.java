package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityDiscussionSummary;
import com.club.agent.entity.ChatMessage;
import com.club.agent.entity.ConceptSession;
import com.club.agent.entity.FileDraftSession;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.FormTemplate;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityDiscussionSummaryMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ChatMessageMapper;
import com.club.agent.mapper.ConceptSessionMapper;
import com.club.agent.mapper.FileDraftSessionMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ActivityFileAiService;
import com.club.agent.vo.ActivityContextVO;
import com.club.agent.vo.FileDraftMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 正式文件撰写 AI 服务实现（E1）。
 * 复用概念阶段模式：权限在 Java（发起人 + 讨论中）、Python 无状态信使、
 * user/tool/assistant 全量落 file_draft_session（事实源）、AI 失败不影响主流程（1035）。
 * 讨论群消息截断策略：最近 30 条、单条 200 字（超长截断，控制工具上下文）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityFileAiServiceImpl implements ActivityFileAiService {

    /** 讨论群消息上下文上限（最近 N 条） */
    private static final int DISCUSSION_LIMIT = 30;
    /** 单条讨论消息截断长度 */
    private static final int DISCUSSION_MSG_MAX = 200;
    /** 问卷自定义题答案汇总：每题最多列答案数 */
    private static final int CUSTOM_ANSWER_MAX = 5;

    private final ActivityMapper activityMapper;
    private final ConceptSessionMapper conceptSessionMapper;
    private final FileDraftSessionMapper draftSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final FormSubmissionMapper formSubmissionMapper;
    private final FormAnswerMapper formAnswerMapper;
    private final ActivityDiscussionSummaryMapper discussionSummaryMapper;
    private final SysUserMapper sysUserMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${ai.draft.base-url:http://127.0.0.1:8094}")
    private String aiBaseUrl;

    @Value("${ai.draft.timeout-seconds:120}")
    private int aiTimeoutSeconds;

    private RestClient pythonClient;

    @Override
    @Transactional
    public List<FileDraftMessageVO> chat(Long clubId, Long activityId, Long userId, String message, String authHeader) {
        Activity activity = ownership.getOwned(clubId, activityId);
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (activity.getStatus() != Activity.STATUS_DISCUSSING
                || activity.getDiscussionClosedAt() == null) {
            // AI 起草仅在讨论关闭后可用（先讨论定稿、再撰写文件）
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        // 1) user 消息先落库：AI 成败，发起人的发言都不丢
        FileDraftSession userMsg = new FileDraftSession();
        userMsg.setActivityId(activityId);
        userMsg.setUserId(userId);
        userMsg.setRole(FileDraftSession.ROLE_USER);
        userMsg.setContent(message);
        draftSessionMapper.insert(userMsg);

        // 2) 调 Python（120s 超时；失败 1035，user 消息保留）
        PythonChatResult result = callPython(clubId, activityId, message, authHeader);

        // 3) 工具调用记录（role=tool；generate_file_draft 的章节草稿 JSON 在 tool_args）
        for (Map<String, Object> t : result.tools()) {
            String toolArgs = String.valueOf(t.get("tool_args"));
            FileDraftSession toolMsg = new FileDraftSession();
            toolMsg.setId(IdWorker.getId());
            toolMsg.setActivityId(activityId);
            toolMsg.setUserId(userId);
            toolMsg.setRole(FileDraftSession.ROLE_TOOL);
            toolMsg.setToolName(String.valueOf(t.get("tool_name")));
            // JSONB 列：空串传 null（CAST 空串失败，K23 先例）
            toolMsg.setToolArgs(StringUtils.hasText(toolArgs) ? toolArgs : null);
            toolMsg.setContent(String.valueOf(t.get("tool_result")));
            draftSessionMapper.insertToolMessage(toolMsg);
        }

        // 4) assistant 消息
        FileDraftSession assistantMsg = new FileDraftSession();
        assistantMsg.setActivityId(activityId);
        assistantMsg.setUserId(userId);
        assistantMsg.setRole(FileDraftSession.ROLE_ASSISTANT);
        assistantMsg.setContent(result.reply());
        draftSessionMapper.insert(assistantMsg);

        return listMessages(activityId);
    }

    @Override
    public List<FileDraftMessageVO> session(Long clubId, Long activityId, Long userId) {
        Activity activity = ownership.getOwned(clubId, activityId);
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return listMessages(activityId);
    }

    @Override
    public ActivityContextVO context(Long clubId, Long activityId, Long userId) {
        Activity activity = ownership.getOwned(clubId, activityId);
        ActivityContextVO vo = new ActivityContextVO();
        // ① 概念批复结果
        ConceptSession concept = conceptSessionMapper.selectById(activity.getConceptId());
        if (concept != null) {
            ActivityContextVO.ConceptVO c = new ActivityContextVO.ConceptVO();
            c.setPlannedTime(concept.getPlannedTime());
            c.setPlannedLocation(concept.getPlannedLocation());
            c.setContent(concept.getContent());
            c.setAiBrief(concept.getAiBrief());
            vo.setConcept(c);
        }
        // ② 讨论群消息：仅高质量（>= 10 字）进参考集；最近 N 条正序，单条截断
        List<ChatMessage> recent = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getActivityId, activityId)
                .eq(ChatMessage::getLowQuality, false)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT " + DISCUSSION_LIMIT));
        List<ActivityContextVO.DiscussionVO> discussions = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage m = recent.get(i);
            ActivityContextVO.DiscussionVO d = new ActivityContextVO.DiscussionVO();
            d.setSenderName(m.getSenderName());
            d.setContent(truncate(m.getContent(), DISCUSSION_MSG_MAX));
            discussions.add(d);
        }
        vo.setDiscussions(discussions);
        // ③ 问卷统计
        vo.setSurvey(surveyStat(activityId));
        // ④ 讨论质量快照（endDiscussion 生成，频率标准数据源）
        vo.setDiscussionStats(discussionStats(activityId));
        return vo;
    }

    // ---------- 内部 ----------

    private ActivityContextVO.DiscussionStatVO discussionStats(Long activityId) {
        ActivityContextVO.DiscussionStatVO stat = new ActivityContextVO.DiscussionStatVO();
        List<ActivityDiscussionSummary> rows = discussionSummaryMapper.selectList(
                new LambdaQueryWrapper<ActivityDiscussionSummary>()
                        .eq(ActivityDiscussionSummary::getActivityId, activityId));
        stat.setTotalMessages(rows.stream().mapToLong(s -> s.getMsgCount() == null ? 0 : s.getMsgCount()).sum());
        stat.setQualityMessages(rows.stream().mapToLong(s -> s.getQualityCount() == null ? 0 : s.getQualityCount()).sum());
        List<ActivityContextVO.HighFreqMemberVO> high = new ArrayList<>();
        for (ActivityDiscussionSummary s : rows) {
            if (!Boolean.TRUE.equals(s.getHighFreq())) {
                continue;
            }
            ActivityContextVO.HighFreqMemberVO h = new ActivityContextVO.HighFreqMemberVO();
            h.setUserId(s.getUserId());
            h.setNickname(sysUserMapper.selectById(s.getUserId()) == null ? "未知" : sysUserMapper.selectById(s.getUserId()).getNickname());
            h.setMsgCount(s.getMsgCount());
            h.setQualityCount(s.getQualityCount());
            high.add(h);
        }
        stat.setHighFreqMembers(high);
        return stat;
    }

    private ActivityContextVO.SurveyStatVO surveyStat(Long activityId) {
        ActivityContextVO.SurveyStatVO stat = new ActivityContextVO.SurveyStatVO();
        FormTemplate survey = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_SURVEY));
        if (survey == null) {
            return stat;
        }
        List<FormField> fields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                .eq(FormField::getTemplateId, survey.getId())
                .orderByAsc(FormField::getSortOrder));
        List<FormSubmission> subs = formSubmissionMapper.selectList(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, survey.getId()));
        stat.setTotalSubmissions((long) subs.size());
        List<Long> subIds = subs.stream().map(FormSubmission::getId).toList();
        if (!subIds.isEmpty()) {
            List<FormAnswer> answers = formAnswerMapper.selectList(new LambdaQueryWrapper<FormAnswer>()
                    .in(FormAnswer::getSubmissionId, subIds));
            // 是否感兴趣（system_flag=1）计数
            FormField interest = fields.stream()
                    .filter(f -> Integer.valueOf(1).equals(f.getSystemFlag()))
                    .findFirst().orElse(null);
            if (interest != null) {
                Map<String, Long> cnt = answers.stream()
                        .filter(a -> a.getFieldId().equals(interest.getId()))
                        .collect(Collectors.groupingBy(a -> String.valueOf(a.getValue()), Collectors.counting()));
                stat.setInterested(cnt.getOrDefault("感兴趣", 0L));
                stat.setNotInterested(cnt.getOrDefault("不感兴趣", 0L));
            }
            // 自定义题答案汇总（每题列前 N 条）
            List<ActivityContextVO.CustomStatVO> stats = new ArrayList<>();
            for (FormField f : fields) {
                if (Integer.valueOf(1).equals(f.getSystemFlag())) {
                    continue;
                }
                List<String> values = answers.stream()
                        .filter(a -> a.getFieldId().equals(f.getId()) && StringUtils.hasText(a.getValue()))
                        .map(FormAnswer::getValue)
                        .limit(CUSTOM_ANSWER_MAX)
                        .toList();
                ActivityContextVO.CustomStatVO cs = new ActivityContextVO.CustomStatVO();
                cs.setLabel(f.getLabel());
                cs.setSummary(values.isEmpty() ? "（无回答）" : String.join("；", values));
                stats.add(cs);
            }
            stat.setCustomStats(stats);
        }
        return stat;
    }

    private PythonChatResult callPython(Long clubId, Long activityId, String message, String authHeader) {
        try {
            RestClient.RequestBodySpec spec = python().post()
                    .uri("/ai/activity-file/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("club_id", String.valueOf(clubId), "activity_id", String.valueOf(activityId), "message", message));
            if (StringUtils.hasText(authHeader)) {
                spec = spec.header("Authorization", authHeader);
            }
            Map<?, ?> resp = spec.retrieve().body(Map.class);
            Object reply = resp == null ? null : resp.get("reply");
            if (reply == null || reply.toString().isBlank()) {
                throw new BizException(ResultCode.BIZ_AI_UNAVAILABLE);
            }
            Object toolsObj = resp == null ? null : resp.get("tools");
            List<Map<String, Object>> tools = toolsObj instanceof List<?> list
                    ? list.stream().map(x -> (Map<String, Object>) x).toList()
                    : List.of();
            return new PythonChatResult(reply.toString(), tools);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用 Python activity-file-chat 失败：activity={} err={}", activityId, e.getMessage());
            throw new BizException(ResultCode.BIZ_AI_UNAVAILABLE);
        }
    }

    private List<FileDraftMessageVO> listMessages(Long activityId) {
        return draftSessionMapper.selectList(new LambdaQueryWrapper<FileDraftSession>()
                        .eq(FileDraftSession::getActivityId, activityId)
                        .orderByAsc(FileDraftSession::getCreatedAt))
                .stream().map(m -> {
                    FileDraftMessageVO vo = new FileDraftMessageVO();
                    vo.setId(m.getId());
                    vo.setActivityId(m.getActivityId());
                    vo.setUserId(m.getUserId());
                    vo.setRole(m.getRole());
                    vo.setContent(m.getContent());
                    vo.setToolName(m.getToolName());
                    vo.setToolArgs(m.getToolArgs());
                    vo.setCreatedAt(m.getCreatedAt());
                    return vo;
                }).toList();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private RestClient python() {
        if (pythonClient == null) {
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(Duration.ofSeconds(5));
            rf.setReadTimeout(Duration.ofSeconds(aiTimeoutSeconds));
            pythonClient = restClientBuilder.baseUrl(aiBaseUrl).requestFactory(rf).build();
        }
        return pythonClient;
    }

    private record PythonChatResult(String reply, List<Map<String, Object>> tools) {
    }
}