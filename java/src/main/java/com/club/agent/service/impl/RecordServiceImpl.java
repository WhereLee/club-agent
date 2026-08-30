package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.RecordSubmitDTO;
import com.club.agent.entity.Activity;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.ActivityRecordScore;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivityRecordScoreMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.RecordService;
import com.club.agent.vo.RecordMemberVO;
import com.club.agent.vo.RecordVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 执行留痕服务实现（块 G）：动态表单引擎第四用例（type=record）。
 * - 窗口：状态=留痕中(7) + record_deadline 截止前（1047）
 * - 覆盖更新：一人一份（uk），截止前可改；fieldId 归属校验防串模板
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordServiceImpl implements RecordService {

    private final ActivityMapper activityMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final FormSubmissionMapper formSubmissionMapper;
    private final FormAnswerMapper formAnswerMapper;
    private final ActivityRecordScoreMapper scoreMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void submit(Long clubId, Long activityId, Long userId, RecordSubmitDTO dto) {
        Activity a = getOwned(clubId, activityId);
        if (a.getStatus() != Activity.STATUS_RECORDING) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        if (a.getRecordDeadline() != null && !LocalDateTime.now().isBefore(a.getRecordDeadline())) {
            throw new BizException(ResultCode.BIZ_RECORD_CLOSED);
        }
        FormTemplate t = recordOf(activityId);
        List<FormField> fields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                .eq(FormField::getTemplateId, t.getId()));
        Map<Long, FormField> fieldMap = fields.stream().collect(Collectors.toMap(FormField::getId, Function.identity()));
        Map<Long, String> answers = new HashMap<>();
        if (dto.getAnswers() != null) {
            for (RecordSubmitDTO.AnswerItem item : dto.getAnswers()) {
                if (!fieldMap.containsKey(item.getFieldId())) {
                    throw new BizException(ResultCode.PARAM_ERROR);
                }
                answers.put(item.getFieldId(), item.getValue());
            }
        }
        // 必填校验（动态字段，Service 校验）
        for (FormField f : fields) {
            if (Integer.valueOf(1).equals(f.getRequired()) && !StringUtils.hasText(answers.get(f.getId()))) {
                throw new BizException(ResultCode.BIZ_RECORD_REQUIRED_FIELD);
            }
        }
        // 覆盖更新：一人一份（删除旧答案重插，保持一致性）
        FormSubmission sub = formSubmissionMapper.selectOne(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, t.getId())
                .eq(FormSubmission::getUserId, userId));
        if (sub != null) {
            formAnswerMapper.delete(new LambdaQueryWrapper<FormAnswer>().eq(FormAnswer::getSubmissionId, sub.getId()));
        } else {
            sub = new FormSubmission();
            sub.setId(IdWorker.getId());
            sub.setTemplateId(t.getId());
            sub.setActivityId(activityId);
            sub.setUserId(userId);
            formSubmissionMapper.insert(sub);
        }
        for (Map.Entry<Long, String> e : answers.entrySet()) {
            FormAnswer fa = new FormAnswer();
            fa.setId(IdWorker.getId());
            fa.setSubmissionId(sub.getId());
            fa.setFieldId(e.getKey());
            fa.setValue(e.getValue());
            formAnswerMapper.insert(fa);
        }
    }

    @Override
    public RecordVO mine(Long clubId, Long activityId, Long userId) {
        getOwned(clubId, activityId);
        FormTemplate t = recordOf(activityId);
        RecordVO vo = new RecordVO();
        vo.setTemplateId(t.getId());
        vo.setTitle(t.getTitle());
        vo.setStatus(t.getStatus());
        List<FormField> fields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                .eq(FormField::getTemplateId, t.getId())
                .orderByAsc(FormField::getSortOrder));
        vo.setFields(fields.stream().map(f -> {
            RecordVO.FieldVO fv = new RecordVO.FieldVO();
            fv.setFieldId(f.getId());
            fv.setLabel(f.getLabel());
            fv.setFieldType(f.getFieldType());
            fv.setRequired(f.getRequired());
            fv.setOptions(parseOptions(f.getOptions()));
            fv.setSortOrder(f.getSortOrder());
            return fv;
        }).toList());
        FormSubmission sub = formSubmissionMapper.selectOne(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, t.getId())
                .eq(FormSubmission::getUserId, userId));
        if (sub != null) {
            vo.setAnswers(answerVO(sub, fields));
            vo.setUpdatedAt(sub.getSubmittedAt());
        }
        return vo;
    }

    @Override
    public List<RecordMemberVO> list(Long clubId, Long activityId) {
        getOwned(clubId, activityId);
        FormTemplate t = recordOf(activityId);
        List<FormField> fields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                .eq(FormField::getTemplateId, t.getId()));
        List<FormSubmission> subs = formSubmissionMapper.selectList(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, t.getId()));
        Map<Long, ActivityRecordScore> scores = scoreMapper.selectList(
                        new LambdaQueryWrapper<ActivityRecordScore>().eq(ActivityRecordScore::getActivityId, activityId))
                .stream().collect(Collectors.toMap(ActivityRecordScore::getUserId, Function.identity()));
        List<RecordMemberVO> list = new ArrayList<>();
        for (FormSubmission sub : subs) {
            RecordMemberVO vo = new RecordMemberVO();
            vo.setUserId(sub.getUserId());
            SysUser u = sysUserMapper.selectById(sub.getUserId());
            vo.setNickname(u == null ? "未知" : u.getNickname());
            vo.setAnswers(answerVO(sub, fields));
            vo.setUpdatedAt(sub.getSubmittedAt());
            vo.setSubmitted(true);
            ActivityRecordScore s = scores.get(sub.getUserId());
            if (s != null) {
                vo.setAiScore(s.getAiScore());
                vo.setAiReason(s.getAiReason());
                vo.setScore(s.getScore());
                vo.setScoreBy(s.getScoreBy());
                vo.setScoreAt(s.getScoreAt());
            }
            list.add(vo);
        }
        return list;
    }

    /** 提交答案 → 带 label 的答案 VO */
    private List<RecordVO.AnswerVO> answerVO(FormSubmission sub, List<FormField> fields) {
        Map<Long, String> labels = fields.stream().collect(Collectors.toMap(FormField::getId, FormField::getLabel));
        return formAnswerMapper.selectList(new LambdaQueryWrapper<FormAnswer>()
                        .eq(FormAnswer::getSubmissionId, sub.getId()))
                .stream().map(x -> {
                    RecordVO.AnswerVO av = new RecordVO.AnswerVO();
                    av.setFieldId(x.getFieldId());
                    av.setLabel(labels.getOrDefault(x.getFieldId(), ""));
                    av.setValue(x.getValue());
                    return av;
                }).toList();
    }

    private List<String> parseOptions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("options 解析失败：{}", json);
            return List.of();
        }
    }

    private Activity getOwned(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        return a;
    }

    private FormTemplate recordOf(Long activityId) {
        FormTemplate t = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_RECORD));
        if (t == null) {
            // 模板由 completeExecution 自动创建；缺失说明状态错乱
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        return t;
    }
}
