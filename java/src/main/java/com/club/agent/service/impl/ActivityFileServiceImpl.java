package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.ActivityFileDTO;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityDuty;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.Membership;
import com.club.agent.entity.Message;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityDutyMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.entity.RbacRole;
import com.club.agent.service.ActivityFileService;
import com.club.agent.service.ActivityService;
import com.club.agent.vo.ActivityFileVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 正式文件域实现（块 D）。
 * 保存语义：章节全量覆盖（删旧字段+答案重插）；发布语义：文件 + 分工一次落库 + 状态推进。
 * 发布后讨论群只读（send 状态校验已实现），全员收正式文件，被指派成员收分工通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityFileServiceImpl implements ActivityFileService {

    private final ActivityMapper activityMapper;
    private final ActivityService activityService;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final FormSubmissionMapper formSubmissionMapper;
    private final FormAnswerMapper formAnswerMapper;
    private final ActivityDutyMapper activityDutyMapper;
    private final MembershipMapper membershipMapper;
    private final SysUserMapper sysUserMapper;
    private final MessageMapper messageMapper;
    private final RbacRoleMapper rbacRoleMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void saveDraft(Long clubId, Long activityId, Long userId, ActivityFileDTO dto) {
        Activity activity = owned(clubId, activityId);
        requireOwner(activity, userId);
        requireClosedDiscussion(activity);
        validateSections(dto);
        writeSections(activityId, userId, dto.getSections());
    }

    @Override
    @Transactional
    public void publish(Long clubId, Long activityId, Long userId, ActivityFileDTO dto) {
        Activity activity = owned(clubId, activityId);
        requireOwner(activity, userId);
        requireStatus(activity, Activity.STATUS_DISCUSSING);
        validateSections(dto);
        validateDuties(dto);
        // 文件章节 + 分工一次落库（同事务）
        writeSections(activityId, userId, dto.getSections());
        writeDuties(activityId, dto.getDuties());
        // 状态机收口：讨论中 → 已发布 + trace file_publish
        activityService.publish(clubId, activityId, userId);
        // 通知：全员收正式文件；被指派成员收分工通知
        notifyFilePublished(activity);
        notifyDuty(activityId, dto.getDuties());
    }

    @Override
    public ActivityFileVO detail(Long clubId, Long activityId, Long userId) {
        Activity activity = owned(clubId, activityId);
        FormTemplate file = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_FILE));
        if (file == null) {
            throw new BizException(ResultCode.BIZ_FILE_NOT_FOUND);
        }
        // 草稿可见性：仅发起人/管理层；发布后全员（Controller 已按 club:member 放行，这里收口）
        if (activity.getStatus() == Activity.STATUS_DISCUSSING && !isManagement(clubId, userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        ActivityFileVO vo = new ActivityFileVO();
        // 章节：字段=标题，答案=内容（发起人提交）
        List<FormField> fields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                .eq(FormField::getTemplateId, file.getId())
                .orderByAsc(FormField::getSortOrder));
        if (!fields.isEmpty()) {
            FormSubmission sub = formSubmissionMapper.selectOne(new LambdaQueryWrapper<FormSubmission>()
                    .eq(FormSubmission::getTemplateId, file.getId())
                    .eq(FormSubmission::getUserId, activity.getUserId()));
            Map<Long, String> answerMap = new LinkedHashMap<>();
            if (sub != null) {
                answerMap = formAnswerMapper.selectList(new LambdaQueryWrapper<FormAnswer>()
                                .eq(FormAnswer::getSubmissionId, sub.getId()))
                        .stream().collect(Collectors.toMap(FormAnswer::getFieldId, FormAnswer::getValue, (a, b) -> a, LinkedHashMap::new));
            }
            List<ActivityFileVO.SectionVO> sections = new ArrayList<>();
            for (FormField f : fields) {
                ActivityFileVO.SectionVO s = new ActivityFileVO.SectionVO();
                s.setTitle(f.getLabel());
                s.setContent(answerMap.getOrDefault(f.getId(), ""));
                sections.add(s);
            }
            vo.setSections(sections);
        }
        // 分工（发布后才有；草稿阶段为空列表）
        List<ActivityDuty> duties = activityDutyMapper.selectList(new LambdaQueryWrapper<ActivityDuty>()
                .eq(ActivityDuty::getActivityId, activityId)
                .orderByAsc(ActivityDuty::getSortOrder));
        if (!duties.isEmpty()) {
            List<Long> allIds = duties.stream()
                    .flatMap(d -> parseIds(d.getAssignedMembers()).stream())
                    .distinct().toList();
            Map<Long, String> names = allIds.isEmpty() ? Map.of() :
                    sysUserMapper.selectBatchIds(allIds).stream()
                            .collect(Collectors.toMap(SysUser::getId, u -> u.getNickname() == null ? u.getUsername() : u.getNickname()));
            List<ActivityFileVO.DutyVO> dutyVOs = new ArrayList<>();
            for (ActivityDuty d : duties) {
                ActivityFileVO.DutyVO dv = new ActivityFileVO.DutyVO();
                dv.setDescription(d.getDescription());
                dv.setMemberIds(d.getAssignedMembers());
                dv.setMemberNames(parseIds(d.getAssignedMembers()).stream()
                        .map(id -> names.getOrDefault(id, String.valueOf(id))).collect(Collectors.joining("、")));
                dutyVOs.add(dv);
            }
            vo.setDuties(dutyVOs);
        } else {
            vo.setDuties(List.of());
        }
        return vo;
    }

    // ---------- 内部 ----------

    /** 章节全量覆盖：删除旧模板（字段+提交+答案）重建（一个活动一份 file 模板） */
    private void writeSections(Long activityId, Long userId, List<ActivityFileDTO.Section> sections) {
        FormTemplate old = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_FILE));
        if (old != null) {
            List<FormField> oldFields = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                    .eq(FormField::getTemplateId, old.getId()));
            if (!oldFields.isEmpty()) {
                List<Long> fieldIds = oldFields.stream().map(FormField::getId).toList();
                formAnswerMapper.delete(new LambdaQueryWrapper<FormAnswer>()
                        .in(FormAnswer::getFieldId, fieldIds));
                formSubmissionMapper.delete(new LambdaQueryWrapper<FormSubmission>()
                        .eq(FormSubmission::getTemplateId, old.getId()));
            }
            formFieldMapper.delete(new LambdaQueryWrapper<FormField>().eq(FormField::getTemplateId, old.getId()));
            formTemplateMapper.deleteById(old.getId());
        }
        FormTemplate file = new FormTemplate();
        file.setId(IdWorker.getId());
        file.setActivityId(activityId);
        file.setType(FormTemplate.TYPE_FILE);
        file.setTitle("正式活动文件");
        file.setStatus(FormTemplate.STATUS_CLOSED);
        file.setCreatedBy(userId);
        formTemplateMapper.insert(file);
        FormSubmission sub = new FormSubmission();
        sub.setId(IdWorker.getId());
        sub.setTemplateId(file.getId());
        sub.setActivityId(activityId);
        sub.setUserId(userId);
        formSubmissionMapper.insert(sub);
        int order = 0;
        for (ActivityFileDTO.Section s : sections) {
            FormField f = new FormField();
            f.setId(IdWorker.getId());
            f.setTemplateId(file.getId());
            f.setLabel(s.getTitle().trim());
            f.setFieldType("textarea");
            f.setRequired(1);
            f.setSortOrder(order++);
            f.setSystemFlag(0);
            formFieldMapper.insertWithOptions(f);
            FormAnswer a = new FormAnswer();
            a.setId(IdWorker.getId());
            a.setSubmissionId(sub.getId());
            a.setFieldId(f.getId());
            a.setValue(s.getContent() == null ? "" : s.getContent());
            formAnswerMapper.insert(a);
        }
    }

    /** 分工全量覆盖（发布时调用） */
    private void writeDuties(Long activityId, List<ActivityFileDTO.Duty> duties) {
        activityDutyMapper.delete(new LambdaQueryWrapper<ActivityDuty>().eq(ActivityDuty::getActivityId, activityId));
        int order = 0;
        for (ActivityFileDTO.Duty d : duties) {
            ActivityDuty duty = new ActivityDuty();
            duty.setId(IdWorker.getId());
            duty.setActivityId(activityId);
            duty.setDescription(d.getDescription().trim());
            duty.setSortOrder(order++);
            try {
                duty.setAssignedMembers(objectMapper.writeValueAsString(d.getMemberIds()));
            } catch (Exception e) {
                throw new BizException(ResultCode.FAIL);
            }
            activityDutyMapper.insertWithMembers(duty);
        }
    }

    private List<Long> parseIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void validateSections(ActivityFileDTO dto) {
        if (dto.getSections() == null || dto.getSections().isEmpty()
                || dto.getSections().stream().anyMatch(s -> s.getTitle() == null || s.getTitle().trim().isEmpty())) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
    }

    private void validateDuties(ActivityFileDTO dto) {
        if (dto.getDuties() == null || dto.getDuties().isEmpty()
                || dto.getDuties().stream().anyMatch(d -> d.getDescription() == null || d.getDescription().trim().isEmpty()
                || d.getMemberIds() == null || d.getMemberIds().isEmpty())) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
    }

    private Activity owned(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        return a;
    }

    private void requireOwner(Activity activity, Long userId) {
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    private void requireStatus(Activity activity, int status) {
        if (activity.getStatus() != status) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
    }

    /** 正式文件仅在讨论关闭后撰写（先讨论定稿、再撰写文件） */
    private void requireClosedDiscussion(Activity activity) {
        if (activity.getStatus() != Activity.STATUS_DISCUSSING || activity.getDiscussionClosedAt() == null) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
    }

    private boolean isManagement(Long clubId, Long userId) {
        List<Long> manageRoleIds = rbacRoleMapper.selectList(new LambdaQueryWrapper<RbacRole>()
                        .eq(RbacRole::getIsManagement, true))
                .stream().map(RbacRole::getId).toList();
        if (manageRoleIds.isEmpty()) {
            return false;
        }
        Long c = membershipMapper.selectCount(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getUserId, userId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED)
                .in(Membership::getRoleId, manageRoleIds));
        return c != null && c > 0;
    }

    private void notifyFilePublished(Activity activity) {
        for (Membership m : approvedMembers(activity.getClubId())) {
            Message msg = new Message();
            msg.setRecipientId(m.getUserId());
            msg.setType(Message.TYPE_ACTIVITY_FILE);
            msg.setTitle("正式文件已发布");
            msg.setContent("「" + briefOf(activity) + "」正式活动文件已发布，活动确定，请查看详情");
            msg.setRefActivityId(activity.getId());
            msg.setReadFlag(0);
            messageMapper.insert(msg);
        }
    }

    private void notifyDuty(Long activityId, List<ActivityFileDTO.Duty> duties) {
        for (ActivityFileDTO.Duty d : duties) {
            for (Long uid : d.getMemberIds()) {
                Message msg = new Message();
                msg.setRecipientId(uid);
                msg.setType(Message.TYPE_ACTIVITY_DUTY);
                msg.setTitle("分工指派");
                msg.setContent("你被指派负责：" + d.getDescription().trim());
                msg.setRefActivityId(activityId);
                msg.setReadFlag(0);
                messageMapper.insert(msg);
            }
        }
    }

    private List<Membership> approvedMembers(Long clubId) {
        return membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED));
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