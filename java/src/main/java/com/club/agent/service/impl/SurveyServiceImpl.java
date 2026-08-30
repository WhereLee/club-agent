package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.SurveyPublishDTO;
import com.club.agent.dto.SurveySubmitDTO;
import com.club.agent.entity.Activity;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.Membership;
import com.club.agent.entity.Message;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.service.ActivityService;
import com.club.agent.service.ChatService;
import com.club.agent.service.SurveyService;
import com.club.agent.vo.SurveyResultVO;
import com.club.agent.vo.SurveyVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 问卷域实现（块 B）。
 * 边界：AI 无写权限；问卷为意向表态（一人一提交，不可重复）；截止时间由发起人定（@Future 注解校验）。
 * 系统内置字段"是否感兴趣"：必答单选，是惩罚机制（不感兴趣 → 报名限制）的数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyServiceImpl implements SurveyService {

    /** 问卷可选字段类型白名单 */
    private static final Set<String> FIELD_TYPES = Set.of("text", "textarea", "radio", "select", "checkbox", "number");
    /** 选项题类型（必须带 options） */
    private static final Set<String> OPTION_TYPES = Set.of("radio", "select", "checkbox");

    private final ActivityMapper activityMapper;
    private final ActivityService activityService;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final FormSubmissionMapper formSubmissionMapper;
    private final FormAnswerMapper formAnswerMapper;
    private final MembershipMapper membershipMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    @Override
    @Transactional
    public SurveyVO publish(Long clubId, Long activityId, Long userId, SurveyPublishDTO dto) {
        Activity activity = getOwned(clubId, activityId);
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (activity.getStatus() != Activity.STATUS_ANNOUNCING) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        // 一个活动一份问卷（uk 兜底；重复发布被 1037 拦截——发布后状态已不是公示中）
        FormTemplate template = new FormTemplate();
        template.setActivityId(activityId);
        template.setType(FormTemplate.TYPE_SURVEY);
        template.setTitle("活动意向问卷");
        template.setDeadline(dto.getDeadline());
        template.setStatus(FormTemplate.STATUS_OPEN);
        template.setCreatedBy(userId);
        formTemplateMapper.insert(template);

        // 系统内置：是否感兴趣（必答单选；惩罚机制数据源）
        insertField(template.getId(), "是否感兴趣", "radio", 1, List.of("感兴趣", "不感兴趣"), 0, FormField.SYSTEM_FLAG_INTEREST);
        // 自定义题（sortOrder 从 1 递增）
        int order = 1;
        if (dto.getFields() != null) {
            for (SurveyPublishDTO.FieldDef f : dto.getFields()) {
                validateField(f);
                insertField(template.getId(), f.getLabel().trim(), f.getFieldType(),
                        Integer.valueOf(1).equals(f.getRequired()) ? 1 : 0, f.getOptions(), order++, 0);
            }
        }

        // 状态机收口：公示中 → 问卷中 + trace
        activityService.startSurvey(clubId, activityId, userId, dto.getDeadline());
        // 全员通知填写
        notifyAllMembers(clubId, Message.TYPE_ACTIVITY_SURVEY, "活动问卷已发布",
                "「" + briefOf(activity) + "」问卷已发布，请于 " + dto.getDeadline() + " 前填写（必答：是否感兴趣）", activityId);
        return detail(clubId, activityId, userId);
    }

    @Override
    public SurveyVO detail(Long clubId, Long activityId, Long userId) {
        FormTemplate template = surveyOf(activityId);
        SurveyVO vo = new SurveyVO();
        vo.setId(template.getId());
        vo.setActivityId(activityId);
        vo.setTitle(template.getTitle());
        vo.setDeadline(template.getDeadline());
        vo.setStatus(template.getStatus());
        Long sub = formSubmissionMapper.selectCount(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, template.getId())
                .eq(FormSubmission::getUserId, userId));
        vo.setSubmitted(sub != null && sub > 0);
        vo.setFields(formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                        .eq(FormField::getTemplateId, template.getId())
                        .orderByAsc(FormField::getSortOrder))
                .stream().map(f -> {
                    SurveyVO.FieldVO fv = new SurveyVO.FieldVO();
                    fv.setId(f.getId());
                    fv.setLabel(f.getLabel());
                    fv.setFieldType(f.getFieldType());
                    fv.setRequired(f.getRequired());
                    fv.setOptions(f.getOptions());
                    fv.setSortOrder(f.getSortOrder());
                    fv.setSystemFlag(f.getSystemFlag());
                    return fv;
                }).toList());
        return vo;
    }

    @Override
    @Transactional
    public void submit(Long clubId, Long activityId, Long userId, SurveySubmitDTO dto) {
        FormTemplate template = surveyOf(activityId);
        if (template.getStatus() != FormTemplate.STATUS_OPEN) {
            throw new BizException(ResultCode.BIZ_SURVEY_CLOSED);
        }
        if (template.getDeadline() != null && !template.getDeadline().isAfter(LocalDateTime.now())) {
            throw new BizException(ResultCode.BIZ_SURVEY_CLOSED);
        }
        Long existed = formSubmissionMapper.selectCount(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, template.getId())
                .eq(FormSubmission::getUserId, userId));
        if (existed != null && existed > 0) {
            throw new BizException(ResultCode.BIZ_SURVEY_ALREADY_SUBMITTED);
        }
        // 答案按 fieldId 收拢；fieldId 必须属于该模板（防串模板注入）
        List<FormField> fields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                .eq(FormField::getTemplateId, template.getId()));
        Map<Long, FormField> fieldMap = fields.stream().collect(Collectors.toMap(FormField::getId, Function.identity()));
        Map<Long, String> answers = new HashMap<>();
        if (dto.getAnswers() != null) {
            for (SurveySubmitDTO.AnswerItem a : dto.getAnswers()) {
                if (!fieldMap.containsKey(a.getFieldId())) {
                    throw new BizException(ResultCode.PARAM_ERROR);
                }
                answers.put(a.getFieldId(), a.getValue());
            }
        }
        // 必填校验（动态字段，Service 校验）
        for (FormField f : fields) {
            if (Integer.valueOf(1).equals(f.getRequired()) && !StringUtils.hasText(answers.get(f.getId()))) {
                throw new BizException(ResultCode.BIZ_SURVEY_REQUIRED_FIELD);
            }
        }
        FormSubmission sub = new FormSubmission();
        sub.setId(IdWorker.getId());
        sub.setTemplateId(template.getId());
        sub.setActivityId(activityId);
        sub.setUserId(userId);
        formSubmissionMapper.insert(sub);
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
    public SurveyResultVO result(Long clubId, Long activityId) {
        FormTemplate template = surveyOf(activityId);
        List<FormField> fields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                .eq(FormField::getTemplateId, template.getId())
                .orderByAsc(FormField::getSortOrder));
        List<FormSubmission> subs = formSubmissionMapper.selectList(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, template.getId()));
        // 全部答案按 fieldId 收拢（value 列表）
        Map<Long, List<String>> answersByField = new HashMap<>();
        if (!subs.isEmpty()) {
            List<FormAnswer> all = formAnswerMapper.selectList(new LambdaQueryWrapper<FormAnswer>()
                    .in(FormAnswer::getSubmissionId, subs.stream().map(FormSubmission::getId).toList()));
            for (FormAnswer a : all) {
                answersByField.computeIfAbsent(a.getFieldId(), k -> new ArrayList<>()).add(a.getValue());
            }
        }
        SurveyResultVO vo = new SurveyResultVO();
        vo.setTemplateId(template.getId());
        vo.setDeadline(template.getDeadline());
        vo.setTotalSubmissions((long) subs.size());
        vo.setFields(fields.stream().map(f -> {
            SurveyResultVO.FieldStatVO fs = new SurveyResultVO.FieldStatVO();
            fs.setFieldId(f.getId());
            fs.setLabel(f.getLabel());
            fs.setFieldType(f.getFieldType());
            List<String> values = answersByField.getOrDefault(f.getId(), List.of());
            if (OPTION_TYPES.contains(f.getFieldType())) {
                // 选项题：按选项计数（多选 value 为 JSON 数组字符串，展平后计数）
                fs.setCounts(countOptions(f, values));
            } else {
                fs.setTexts(values.stream().filter(StringUtils::hasText).toList());
            }
            return fs;
        }).toList());
        return vo;
    }

    @Override
    @Transactional
    public void startDiscuss(Long clubId, Long activityId, Long userId) {
        Activity activity = getOwned(clubId, activityId);
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (activity.getStatus() != Activity.STATUS_SURVEYING) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        // 状态机收口：问卷中 → 讨论中 + trace
        activityService.startDiscuss(clubId, activityId, userId);
        // 模板关闭（不可再提交）
        formTemplateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FormTemplate>()
                .eq(FormTemplate::getId, surveyOf(activityId).getId())
                .set(FormTemplate::getStatus, FormTemplate.STATUS_CLOSED));
        // 块 C：统一生成入群名单快照（感兴趣成员 ∪ 管理层）+ 入群通知（幂等）
        chatService.syncMembers(clubId, activityId);
    }

    /** 系统内置"是否感兴趣"字段的选项解析与计数（多选展平） */
    private List<SurveyResultVO.OptionCountVO> countOptions(FormField f, List<String> values) {
        List<String> options = parseOptions(f.getOptions());
        Map<String, Long> count = new LinkedHashMap<>();
        for (String o : options) {
            count.put(o, 0L);
        }
        for (String v : values) {
            if (!StringUtils.hasText(v)) {
                continue;
            }
            List<String> picked;
            if (f.getFieldType().equals("checkbox")) {
                picked = parseOptions(v);  // 多选答案也是 JSON 数组字符串
            } else {
                picked = List.of(v);
            }
            for (String p : picked) {
                count.merge(p, 1L, Long::sum);
            }
        }
        return count.entrySet().stream()
                .map(e -> {
                    SurveyResultVO.OptionCountVO oc = new SurveyResultVO.OptionCountVO();
                    oc.setOption(e.getKey());
                    oc.setCount(e.getValue());
                    return oc;
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

    private void validateField(SurveyPublishDTO.FieldDef f) {
        String type = f.getFieldType();
        if (!FIELD_TYPES.contains(type)) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        if (OPTION_TYPES.contains(type) && (f.getOptions() == null || f.getOptions().isEmpty())) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
    }

    private void insertField(Long templateId, String label, String fieldType, int required,
                             List<String> options, int sortOrder, int systemFlag) {
        FormField f = new FormField();
        f.setId(IdWorker.getId());
        f.setTemplateId(templateId);
        f.setLabel(label);
        f.setFieldType(fieldType);
        f.setRequired(required);
        f.setSortOrder(sortOrder);
        f.setSystemFlag(systemFlag);
        if (options != null && !options.isEmpty()) {
            try {
                f.setOptions(objectMapper.writeValueAsString(options));
            } catch (Exception e) {
                throw new BizException(ResultCode.FAIL);
            }
        }
        formFieldMapper.insertWithOptions(f);
    }

    private Activity getOwned(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        return a;
    }

    private FormTemplate surveyOf(Long activityId) {
        FormTemplate t = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_SURVEY));
        if (t == null) {
            throw new BizException(ResultCode.BIZ_SURVEY_NOT_FOUND);
        }
        return t;
    }

    /** 该社团全部已通过成员 */
    private List<Membership> approvedMembers(Long clubId) {
        return membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED));
    }

    /** 站内消息：问卷发布通知全员 */
    private void notifyAllMembers(Long clubId, String type, String title, String content, Long refActivityId) {
        for (Membership m : approvedMembers(clubId)) {
            Message msg = new Message();
            msg.setRecipientId(m.getUserId());
            msg.setType(type);
            msg.setTitle(title);
            msg.setContent(content);
            msg.setRefActivityId(refActivityId);
            msg.setReadFlag(0);
            messageMapper.insert(msg);
        }
    }

    private static String briefOf(Activity a) {
        String t = a.getPlannedTime();
        String loc = a.getPlannedLocation();
        if (!isBlank(t) && !isBlank(loc)) {
            return t + " " + loc;
        }
        String c = a.getContent();
        if (isBlank(c)) {
            return "活动";
        }
        return c.length() > 20 ? c.substring(0, 20) + "…" : c;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}