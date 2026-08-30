package com.club.agent.service.impl;

import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySignup;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.Message;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报名子流程单测（Q3 补网）：状态门 / 截止拒绝 / 不感兴趣拦截 / 在线协助放行+通知发起人。
 */
@ExtendWith(MockitoExtension.class)
class SignupServiceImplTest {

    @Mock ActivityMapper activityMapper;
    @Mock ActivitySignupMapper signupMapper;
    @Mock FormTemplateMapper formTemplateMapper;
    @Mock FormFieldMapper formFieldMapper;
    @Mock FormSubmissionMapper formSubmissionMapper;
    @Mock FormAnswerMapper formAnswerMapper;
    @Mock MembershipMapper membershipMapper;
    @Mock MessageMapper messageMapper;
    @Mock SysUserMapper sysUserMapper;

    @InjectMocks SignupServiceImpl signupService;

    final Long CLUB = 100L;
    final Long ACTIVITY = 200L;
    final Long USER = 300L;

    private Activity activity(int status) {
        Activity a = new Activity();
        a.setId(ACTIVITY);
        a.setClubId(CLUB);
        a.setUserId(USER);
        a.setStatus(status);
        a.setSignupDeadline(LocalDateTime.now().plusHours(1));
        return a;
    }

    /** 问卷链路 mock：survey 模板 + system_flag=1 字段 + 已提交 + 答案 */
    private void mockSurvey(String answerValue) {
        FormTemplate survey = new FormTemplate();
        survey.setId(1L);
        when(formTemplateMapper.selectOne(any())).thenReturn(survey);
        FormField field = new FormField();
        field.setId(2L);
        field.setTemplateId(1L);
        field.setSystemFlag(1);
        when(formFieldMapper.selectList(any())).thenReturn(List.of(field));
        FormSubmission sub = new FormSubmission();
        sub.setId(3L);
        when(formSubmissionMapper.selectOne(any())).thenReturn(sub);
        FormAnswer ans = new FormAnswer();
        ans.setValue(answerValue);
        when(formAnswerMapper.selectOne(any())).thenReturn(ans);
    }

    @Test
    @DisplayName("状态门：非报名中 → 活动状态拒绝，不落票")
    void signup_wrongState_rejected() {
        when(activityMapper.selectById(ACTIVITY)).thenReturn(activity(Activity.STATUS_PUBLISHED));

        assertThatThrownBy(() -> signupService.signup(CLUB, ACTIVITY, USER, ActivitySignup.CHOICE_PARTICIPATE, false))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN.getCode());
        verify(signupMapper, never()).insert(any(ActivitySignup.class));
    }

    @Test
    @DisplayName("截止拒绝：报名窗口已过 → 报名截止业务码，不落票")
    void signup_afterDeadline_rejected() {
        Activity a = activity(Activity.STATUS_SIGNUP);
        a.setSignupDeadline(LocalDateTime.now().minusMinutes(1));
        when(activityMapper.selectById(ACTIVITY)).thenReturn(a);

        assertThatThrownBy(() -> signupService.signup(CLUB, ACTIVITY, USER, ActivitySignup.CHOICE_PARTICIPATE, false))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BIZ_SIGNUP_CLOSED.getCode());
        verify(signupMapper, never()).insert(any(ActivitySignup.class));
    }

    @Test
    @DisplayName("不感兴趣拦截：问卷答不感兴趣且选参加 → 拦截；新增/修改均不落票")
    void signup_notInterested_blocked() {
        when(activityMapper.selectById(ACTIVITY)).thenReturn(activity(Activity.STATUS_SIGNUP));
        mockSurvey("不感兴趣");

        assertThatThrownBy(() -> signupService.signup(CLUB, ACTIVITY, USER, ActivitySignup.CHOICE_PARTICIPATE, false))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BIZ_SIGNUP_NOT_INTERESTED.getCode());
        verify(signupMapper, never()).insert(any(ActivitySignup.class));
        verify(signupMapper, never()).updateById(any(ActivitySignup.class));
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    @DisplayName("在线协助放行：不参加+assist 不触发问卷拦截 → 落票 + 通知发起人")
    void signup_onlineAssist_allowedAndNotify() {
        when(activityMapper.selectById(ACTIVITY)).thenReturn(activity(Activity.STATUS_SIGNUP));
        when(signupMapper.selectOne(any())).thenReturn(null);

        signupService.signup(CLUB, ACTIVITY, USER, ActivitySignup.CHOICE_NOT_PARTICIPATE, true);

        verify(signupMapper).insert(any(ActivitySignup.class));
        verify(messageMapper).insert(any(Message.class)); // 提示发起人
    }
}
