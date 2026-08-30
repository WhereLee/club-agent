package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.RecordScoreDTO;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityAttendance;
import com.club.agent.entity.ActivityRecordScore;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityAttendanceMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivityRecordScoreMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.RecordScoreService;
import com.club.agent.vo.RecordScoreVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 留痕打分服务实现（块 H）：
 * - AI 预评：输入留痕答案 + 签到事实 → 输出 {score, reason} 建议值（不落库）
 * - 手动打分：管理员确认后落库（uk 一人一档，重复 1049）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordScoreServiceImpl implements RecordScoreService {

    private static final String REVIEW_PROMPT = "你是社团活动执行留痕的评分助手。请根据成员提交的执行留痕和签到情况，给出 0-100 分的建议分及评分理由。\n"
            + "评分参考：\n"
            + "- 工作内容是否具体、可核验（40 分）\n"
            + "- 完成情况是否明确（30 分）\n"
            + "- 补充说明是否有价值（20 分）\n"
            + "- 是否签到到场（10 分，未签到扣分）\n"
            + "严格输出 JSON：{\"score\": 85, \"reason\": \"不超过 80 字的一句话理由\"}\n"
            + "不要输出任何其他文字（不要代码块标记）。";

    private final ActivityMapper activityMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final FormSubmissionMapper formSubmissionMapper;
    private final FormAnswerMapper formAnswerMapper;
    private final ActivityAttendanceMapper attendanceMapper;
    private final ActivityRecordScoreMapper scoreMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    public RecordScoreVO preview(Long clubId, Long activityId, Long userId) {
        Activity a = requireRecording(clubId, activityId);
        FormSubmission sub = submissionOf(activityId, userId);
        if (sub == null) {
            throw new BizException(ResultCode.BIZ_RECORD_NOT_SUBMITTED);
        }
        // 签到事实
        boolean checked = attendanceMapper.selectCount(new LambdaQueryWrapper<ActivityAttendance>()
                .eq(ActivityAttendance::getActivityId, activityId)
                .eq(ActivityAttendance::getUserId, userId)) > 0;
        // 留痕答案（label: value 拼接）
        List<FormField> fields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                .eq(FormField::getTemplateId, sub.getTemplateId()));
        Map<Long, String> labels = fields.stream().collect(Collectors.toMap(FormField::getId, FormField::getLabel));
        StringBuilder content = new StringBuilder("留痕内容：\n");
        for (FormAnswer ans : formAnswerMapper.selectList(new LambdaQueryWrapper<FormAnswer>()
                .eq(FormAnswer::getSubmissionId, sub.getId()))) {
            content.append(labels.getOrDefault(ans.getFieldId(), "字段")).append("：")
                    .append(ans.getValue() == null ? "" : ans.getValue()).append('\n');
        }
        content.append("签到情况：").append(checked ? "已签到" : "未签到");
        String raw = chatClient.prompt()
                .system(REVIEW_PROMPT)
                .user(content.toString())
                .call().content();
        log.info("AI 留痕预评返回：{}", truncate(raw, 300));
        RecordScoreVO vo = new RecordScoreVO();
        vo.setUserId(userId);
        SysUser u = sysUserMapper.selectById(userId);
        vo.setNickname(u == null ? "未知" : u.getNickname());
        vo.setSubmitted(true);
        vo.setCheckedIn(checked);
        JsonNode node = parseObject(raw);
        if (node != null) {
            vo.setAiScore(Math.max(0, Math.min(100, node.path("score").asInt(0))));
            vo.setAiReason(node.path("reason").asText(""));
        }
        return vo;
    }

    @Override
    @Transactional
    public void score(Long clubId, Long activityId, Long operatorId, RecordScoreDTO dto) {
        requireRecording(clubId, activityId);
        if (submissionOf(activityId, dto.getUserId()) == null) {
            throw new BizException(ResultCode.BIZ_RECORD_NOT_SUBMITTED);
        }
        Long existed = scoreMapper.selectCount(new LambdaQueryWrapper<ActivityRecordScore>()
                .eq(ActivityRecordScore::getActivityId, activityId)
                .eq(ActivityRecordScore::getUserId, dto.getUserId()));
        if (existed != null && existed > 0) {
            throw new BizException(ResultCode.BIZ_RECORD_SCORE_DUPLICATE);
        }
        ActivityRecordScore s = new ActivityRecordScore();
        s.setId(IdWorker.getId());
        s.setActivityId(activityId);
        s.setUserId(dto.getUserId());
        s.setScore(dto.getScore());
        s.setScoreBy(operatorId);
        s.setScoreAt(LocalDateTime.now());
        scoreMapper.insert(s);
    }

    @Override
    public List<RecordScoreVO> list(Long clubId, Long activityId) {
        Activity a = requireRecording(clubId, activityId);
        // 已提交留痕者 + 打分状态
        FormTemplate t = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_RECORD));
        List<RecordScoreVO> list = new ArrayList<>();
        if (t == null) {
            return list;
        }
        Map<Long, ActivityRecordScore> scores = scoreMapper.selectList(
                        new LambdaQueryWrapper<ActivityRecordScore>().eq(ActivityRecordScore::getActivityId, activityId))
                .stream().collect(Collectors.toMap(ActivityRecordScore::getUserId, Function.identity()));
        for (FormSubmission sub : formSubmissionMapper.selectList(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, t.getId()))) {
            RecordScoreVO vo = new RecordScoreVO();
            vo.setUserId(sub.getUserId());
            SysUser u = sysUserMapper.selectById(sub.getUserId());
            vo.setNickname(u == null ? "未知" : u.getNickname());
            vo.setSubmitted(true);
            ActivityRecordScore s = scores.get(sub.getUserId());
            if (s != null) {
                vo.setScore(s.getScore());
                vo.setAiScore(s.getAiScore());
                vo.setAiReason(s.getAiReason());
                vo.setScoreBy(s.getScoreBy());
                vo.setScoreAt(s.getScoreAt());
            }
            list.add(vo);
        }
        return list;
    }

    // ---- 私有 ----

    private Activity requireRecording(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        if (a.getStatus() != Activity.STATUS_RECORDING) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        return a;
    }

    private FormSubmission submissionOf(Long activityId, Long userId) {
        FormTemplate t = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_RECORD));
        if (t == null) {
            return null;
        }
        return formSubmissionMapper.selectOne(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, t.getId())
                .eq(FormSubmission::getUserId, userId));
    }

    private JsonNode parseObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(s.substring(start, end + 1));
        } catch (Exception e) {
            log.warn("AI 预评 JSON 解析失败：{}", truncate(raw, 200));
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
