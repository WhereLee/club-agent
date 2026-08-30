package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySignup;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.Membership;
import com.club.agent.entity.Message;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySignupMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.SignupService;
import com.club.agent.vo.SignupMemberVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 报名服务实现（块 F）：
 * - 报名窗口：状态=报名中(5) 且 未过 signup_deadline（超时 1044）
 * - 拦截：问卷 system_flag=1 答"不感兴趣"者限制参加（participate 1045）；在线协助放行
 * - 在线协助（不参加+assist）→ 通知发起人（远程支持提示）
 * - 名单：全员 + 报名状态 + 拦截标记（管理层视图）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupServiceImpl implements SignupService {

    private static final String NOT_INTERESTED = "不感兴趣";

    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final FormSubmissionMapper formSubmissionMapper;
    private final FormAnswerMapper formAnswerMapper;
    private final MembershipMapper membershipMapper;
    private final MessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    @Transactional
    public void signup(Long clubId, Long activityId, Long userId, String choice, Boolean onlineAssist) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        if (a.getStatus() != Activity.STATUS_SIGNUP) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        // 截止校验：报名窗口截止后拒绝（deadline 必设，开始报名时强校验）
        if (a.getSignupDeadline() != null && !LocalDateTime.now().isBefore(a.getSignupDeadline())) {
            throw new BizException(ResultCode.BIZ_SIGNUP_CLOSED);
        }
        boolean assist = Boolean.TRUE.equals(onlineAssist);
        boolean participate = ActivitySignup.CHOICE_PARTICIPATE.equals(choice);
        // 不感兴趣拦截：仅限制"参加"；在线协助（远程支持）放行
        if (participate && isNotInterested(activityId, userId)) {
            throw new BizException(ResultCode.BIZ_SIGNUP_NOT_INTERESTED);
        }
        ActivitySignup exist = signupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId));
        if (exist != null) {
            // 修改报名：覆盖（截止前）
            exist.setChoice(choice);
            exist.setOnlineAssist(assist && !participate);
            exist.setUpdatedAt(LocalDateTime.now());
            signupMapper.updateById(exist);
        } else {
            ActivitySignup s = new ActivitySignup();
            s.setId(IdWorker.getId());
            s.setActivityId(activityId);
            s.setUserId(userId);
            s.setChoice(choice);
            s.setOnlineAssist(assist && !participate);
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            signupMapper.insert(s);
        }
        // 在线协助 → 提示发起人（无论新增/修改）
        if (!participate && assist) {
            notifyOwner(a, userId, "有成员申请在线协助",
                    "有成员申请了本次活动的在线协助（远程支持），请到报名名单查看并联系确认", activityId);
        }
    }

    @Override
    public List<SignupMemberVO> list(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        // 全员（已通过成员）
        List<Membership> members = membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED));
        Map<Long, ActivitySignup> signups = signupMapper.selectList(
                        new LambdaQueryWrapper<ActivitySignup>().eq(ActivitySignup::getActivityId, activityId))
                .stream().collect(Collectors.toMap(ActivitySignup::getUserId, Function.identity()));
        List<SignupMemberVO> list = new ArrayList<>();
        for (Membership m : members) {
            SignupMemberVO vo = new SignupMemberVO();
            vo.setUserId(m.getUserId());
            SysUser u = sysUserMapper.selectById(m.getUserId());
            vo.setNickname(u == null ? "未知" : u.getNickname());
            ActivitySignup s = signups.get(m.getUserId());
            if (s != null) {
                vo.setChoice(s.getChoice());
                vo.setOnlineAssist(s.getOnlineAssist());
                vo.setSignupAt(s.getCreatedAt());
            }
            vo.setBlocked(isNotInterested(activityId, m.getUserId()));
            list.add(vo);
        }
        return list;
    }

    /** 问卷是否答"不感兴趣"（system_flag=1 字段，复用 surveyStat 先例） */
    private boolean isNotInterested(Long activityId, Long userId) {
        FormTemplate survey = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_SURVEY));
        if (survey == null) {
            return false;
        }
        FormField interest = formFieldMapper.selectList(new LambdaQueryWrapper<FormField>()
                        .eq(FormField::getTemplateId, survey.getId())
                        .eq(FormField::getSystemFlag, 1))
                .stream().findFirst().orElse(null);
        if (interest == null) {
            return false;
        }
        FormSubmission sub = formSubmissionMapper.selectOne(new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getTemplateId, survey.getId())
                .eq(FormSubmission::getUserId, userId));
        if (sub == null) {
            return false;
        }
        FormAnswer ans = formAnswerMapper.selectOne(new LambdaQueryWrapper<FormAnswer>()
                .eq(FormAnswer::getSubmissionId, sub.getId())
                .eq(FormAnswer::getFieldId, interest.getId()));
        return ans != null && NOT_INTERESTED.equals(ans.getValue());
    }

    /** 站内消息：提示发起人 */
    private void notifyOwner(Activity a, Long applicantId, String title, String content, Long refActivityId) {
        Message msg = new Message();
        msg.setRecipientId(a.getUserId());
        msg.setType(Message.TYPE_ACTIVITY_ONLINE_ASSIST);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setRefActivityId(refActivityId);
        msg.setReadFlag(0);
        messageMapper.insert(msg);
    }
}
