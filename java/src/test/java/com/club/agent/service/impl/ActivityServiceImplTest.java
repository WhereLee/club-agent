package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityDiscussionSummary;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.entity.ActivityTrace;
import com.club.agent.entity.ChatMessage;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.Membership;
import com.club.agent.entity.Message;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityDiscussionSummaryMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.mapper.ActivityTraceMapper;
import com.club.agent.mapper.ChatMessageMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.SummaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动状态机白盒单测（块 A 收口 + 块 F/G 扩展）：
 * 覆盖 CAS 条件流转、发起人权限、并发冲突（update 影响 0）、trace 单出口、讨论质量快照聚合。
 * Mock 全部 Mapper，不连数据库。
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    @Mock ActivityMapper activityMapper;
    @Mock ActivitySummaryMapper summaryMapper;
    @Mock ActivityTraceMapper activityTraceMapper;
    @Mock ChatMessageMapper chatMessageMapper;
    @Mock ActivityDiscussionSummaryMapper discussionSummaryMapper;
    @Mock MembershipMapper membershipMapper;
    @Mock MessageMapper messageMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock FormTemplateMapper formTemplateMapper;
    @Mock FormFieldMapper formFieldMapper;
    @Mock ObjectMapper objectMapper;
    @Mock SummaryService summaryService;

    @InjectMocks ActivityServiceImpl activityService;

    final Long CLUB = 100L;
    final Long ACT = 200L;
    final Long OWNER = 300L;
    final Long OTHER = 301L;

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mockito 单测无 Spring 上下文：MP 的 TableInfo 缓存为空，Lambda*Wrapper 构造会失败，
        // 手动为 Service 用到的 Lambda 实体初始化（官方单测同法）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(Activity.class, ChatMessage.class, FormTemplate.class, Membership.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    @BeforeEach
    void setUp() {
        // @Value 字段在纯 Mockito 单测不注入，手动设置阈值（默认 3）
        ReflectionTestUtils.setField(activityService, "highFreqMinMsgs", 3);
    }

    // ---- 工具 ----

    private Activity ownerActivity(int status) {
        Activity a = new Activity();
        a.setId(ACT);
        a.setClubId(CLUB);
        a.setUserId(OWNER);
        a.setStatus(status);
        return a;
    }

    private void mockOwned(Activity a) {
        when(activityMapper.selectById(ACT)).thenReturn(a);
    }

    private void mockTraceOk() {
        when(activityMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
    }

    private BizException expectStateForbidden(Throwable t) {
        return (BizException) t;
    }

    // ---- endDiscussion（讨论关闭：3 → 关闭标记） ----

    @Test
    @DisplayName("endDiscussion 成功：CAS 更新 + trace 单出口")
    void endDiscussion_success() {
        mockOwned(ownerActivity(Activity.STATUS_DISCUSSING));
        mockTraceOk();
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());

        activityService.endDiscussion(CLUB, ACT, OWNER);

        verify(activityMapper).update(any(), any(LambdaUpdateWrapper.class));
        ArgumentCaptor<ActivityTrace> cap = ArgumentCaptor.forClass(ActivityTrace.class);
        verify(activityTraceMapper).insert(cap.capture());
        assertThat(cap.getValue().getActivityId()).isEqualTo(ACT);
        assertThat(cap.getValue().getAction()).isEqualTo(ActivityTrace.ACTION_END_DISCUSSION);
        verify(discussionSummaryMapper, never()).insert(any(ActivityDiscussionSummary.class));
    }

    @Test
    @DisplayName("endDiscussion 非发起人：403 拒绝")
    void endDiscussion_notOwner_forbidden() {
        Activity a = ownerActivity(Activity.STATUS_DISCUSSING);
        a.setUserId(OTHER);
        mockOwned(a);

        assertThatThrownBy(() -> activityService.endDiscussion(CLUB, ACT, OWNER))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(expectStateForbidden(t).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
        verify(activityMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("endDiscussion 状态不符/已关闭：CAS 影响 0 → 1037（幂等拒绝）")
    void endDiscussion_stateForbidden() {
        mockOwned(ownerActivity(Activity.STATUS_DISCUSSING));
        when(activityMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> activityService.endDiscussion(CLUB, ACT, OWNER))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(expectStateForbidden(t).getCode())
                        .isEqualTo(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN.getCode()));
        verify(activityTraceMapper, never()).insert(any(ActivityTrace.class));
    }

    @Test
    @DisplayName("endDiscussion 构建质量快照：按发送者聚合消息数/高质量数/高频标记")
    void endDiscussion_buildSummary() {
        mockOwned(ownerActivity(Activity.STATUS_DISCUSSING));
        mockTraceOk();
        // sender A: 3 条全高质量 → 高频；sender B: 2 条含 1 低质量 → 非高频
        ChatMessage a1 = msg(11L, false), a2 = msg(11L, false), a3 = msg(11L, false);
        ChatMessage b1 = msg(22L, true), b2 = msg(22L, false);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(a1, a2, a3, b1, b2));

        activityService.endDiscussion(CLUB, ACT, OWNER);

        ArgumentCaptor<ActivityDiscussionSummary> cap = ArgumentCaptor.forClass(ActivityDiscussionSummary.class);
        verify(discussionSummaryMapper, times(2)).insert(cap.capture());
        ActivityDiscussionSummary sA = cap.getAllValues().stream()
                .filter(s -> s.getUserId().equals(11L)).findFirst().orElseThrow();
        ActivityDiscussionSummary sB = cap.getAllValues().stream()
                .filter(s -> s.getUserId().equals(22L)).findFirst().orElseThrow();
        assertThat(sA.getMsgCount()).isEqualTo(3);
        assertThat(sA.getQualityCount()).isEqualTo(3);
        assertThat(sA.getHighFreq()).isTrue();
        assertThat(sB.getMsgCount()).isEqualTo(2);
        assertThat(sB.getQualityCount()).isEqualTo(1);
        assertThat(sB.getHighFreq()).isFalse();
    }

    // ---- startSignup（报名：4 → 5） ----

    @Test
    @DisplayName("startSignup 缺截止时间：400（先过归属校验）")
    void startSignup_deadlineNull() {
        mockOwned(ownerActivity(Activity.STATUS_PUBLISHED));

        assertThatThrownBy(() -> activityService.startSignup(CLUB, ACT, OWNER, null))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(expectStateForbidden(t).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("startSignup 成功：CAS + trace + 全员通知")
    void startSignup_success() {
        mockOwned(ownerActivity(Activity.STATUS_PUBLISHED));
        mockTraceOk();
        when(membershipMapper.selectList(any())).thenReturn(List.of(member(1L), member(2L)));
        when(sysUserMapper.selectById(any())).thenReturn(user("社长"));

        activityService.startSignup(CLUB, ACT, OWNER, LocalDateTime.of(2026, 9, 10, 20, 0));

        verify(messageMapper, times(2)).insert(any(Message.class));
        ArgumentCaptor<ActivityTrace> cap = ArgumentCaptor.forClass(ActivityTrace.class);
        verify(activityTraceMapper).insert(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo(ActivityTrace.ACTION_START_SIGNUP);
    }

    @Test
    @DisplayName("startSignup 状态不符：CAS 影响 0 → 1037")
    void startSignup_stateForbidden() {
        mockOwned(ownerActivity(Activity.STATUS_SIGNUP));
        when(activityMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> activityService.startSignup(CLUB, ACT, OWNER, LocalDateTime.now()))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(expectStateForbidden(t).getCode())
                        .isEqualTo(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN.getCode()));
    }

    // ---- startExecution（执行：5 → 6） ----

    @Test
    @DisplayName("startExecution 成功：trace 含留痕截止")
    void startExecution_success() {
        mockOwned(ownerActivity(Activity.STATUS_SIGNUP));
        mockTraceOk();
        when(sysUserMapper.selectById(any())).thenReturn(user("社长"));

        activityService.startExecution(CLUB, ACT, OWNER, LocalDateTime.of(2026, 9, 12, 20, 0));

        ArgumentCaptor<ActivityTrace> cap = ArgumentCaptor.forClass(ActivityTrace.class);
        verify(activityTraceMapper).insert(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo(ActivityTrace.ACTION_START_EXECUTION);
        assertThat(cap.getValue().getDetail()).contains("留痕截止时间");
    }

    @Test
    @DisplayName("startExecution 未设截止：留痕截止条件 SET 跳过，仍可流转")
    void startExecution_nullDeadline() {
        mockOwned(ownerActivity(Activity.STATUS_SIGNUP));
        mockTraceOk();
        when(sysUserMapper.selectById(any())).thenReturn(user("社长"));

        activityService.startExecution(CLUB, ACT, OWNER, null);

        verify(activityMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    // ---- completeExecution（留痕：6 → 7 + 模板幂等创建） ----

    @Test
    @DisplayName("completeExecution 首次：自动创建留痕模板（3 个固定字段，radio options JSON 序列化）")
    void completeExecution_createTemplate() throws Exception {
        mockOwned(ownerActivity(Activity.STATUS_EXECUTING));
        mockTraceOk();
        when(formTemplateMapper.selectOne(any())).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("[\"已完成\",\"进行中\",\"受阻\"]");
        when(membershipMapper.selectList(any())).thenReturn(List.of());
        when(sysUserMapper.selectById(any())).thenReturn(user("社长"));

        activityService.completeExecution(CLUB, ACT, OWNER);

        verify(formTemplateMapper).insert(any(FormTemplate.class));
        ArgumentCaptor<FormField> fCap = ArgumentCaptor.forClass(FormField.class);
        verify(formFieldMapper, times(3)).insertWithOptions(fCap.capture());
        assertThat(fCap.getAllValues())
                .extracting(FormField::getLabel)
                .containsExactly("工作内容", "完成情况", "补充说明");
        FormField radio = fCap.getAllValues().get(1);
        assertThat(radio.getFieldType()).isEqualTo("radio");
        assertThat(radio.getOptions()).isEqualTo("[\"已完成\",\"进行中\",\"受阻\"]");
        ArgumentCaptor<ActivityTrace> cap = ArgumentCaptor.forClass(ActivityTrace.class);
        verify(activityTraceMapper).insert(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo(ActivityTrace.ACTION_COMPLETE_EXECUTION);
    }

    @Test
    @DisplayName("completeExecution 模板已存在：幂等不重复建")
    void completeExecution_templateExists() {
        mockOwned(ownerActivity(Activity.STATUS_EXECUTING));
        mockTraceOk();
        FormTemplate existed = new FormTemplate();
        existed.setId(999L);
        when(formTemplateMapper.selectOne(any())).thenReturn(existed);
        when(membershipMapper.selectList(any())).thenReturn(List.of());
        when(sysUserMapper.selectById(any())).thenReturn(user("社长"));

        activityService.completeExecution(CLUB, ACT, OWNER);

        verify(formTemplateMapper, never()).insert(any(FormTemplate.class));
        verify(formFieldMapper, never()).insertWithOptions(any(FormField.class));
    }

    // ---- closeRecords（总结：7 → 8，手动/系统双入口） ----

    @Test
    @DisplayName("closeRecords 手动：校验发起人 + trace 记录操作人 + 自动触发总结生成")
    void closeRecords_manual() {
        mockOwned(ownerActivity(Activity.STATUS_RECORDING));
        mockTraceOk();
        when(sysUserMapper.selectById(any())).thenReturn(user("社长"));

        activityService.closeRecords(CLUB, ACT, OWNER, false);

        // 进入总结中自动触发总结生成（userId=null 表示系统动作）
        verify(summaryService).generate(CLUB, ACT, null);
        ArgumentCaptor<ActivityTrace> cap = ArgumentCaptor.forClass(ActivityTrace.class);
        verify(activityTraceMapper).insert(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo(ActivityTrace.ACTION_RECORD_CLOSE);
        assertThat(cap.getValue().getOperatorId()).isEqualTo(OWNER);
    }

    @Test
    @DisplayName("closeRecords 系统扫描：跳过发起人校验 + trace 操作人=null")
    void closeRecords_system() {
        mockTraceOk();

        activityService.closeRecords(CLUB, ACT, null, true);

        ArgumentCaptor<ActivityTrace> cap = ArgumentCaptor.forClass(ActivityTrace.class);
        verify(activityTraceMapper).insert(cap.capture());
        assertThat(cap.getValue().getOperatorId()).isNull();
        assertThat(cap.getValue().getOperatorName()).isEqualTo("系统");
    }

    @Test
    @DisplayName("closeRecords 生成提交被拒（C3）：状态推进与 trace 不中断，落 failed 行待调度重试")
    void closeRecords_generateRejected_upsertFailedRow() {
        mockTraceOk();
        doThrow(new TaskRejectedException("aiExecutor 已满"))
                .when(summaryService).generate(CLUB, ACT, null);
        when(summaryMapper.selectOne(any())).thenReturn(null);

        activityService.closeRecords(CLUB, ACT, null, true);

        // 状态推进已生效（无异常抛出）+ trace 已记录
        verify(activityTraceMapper).insert(any(ActivityTrace.class));
        // 被拒时 generate 方法体未执行、无 summary 行 → 必须 insert failed 行
        ArgumentCaptor<ActivitySummary> cap = ArgumentCaptor.forClass(ActivitySummary.class);
        verify(summaryMapper).insert(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ActivitySummary.STATUS_FAILED);
        assertThat(cap.getValue().getActivityId()).isEqualTo(ACT);
    }

    @Test
    @DisplayName("closeRecords 生成提交被拒（C3）：已存在 summary 行 → update 置 failed（不消耗重试名额）")
    void closeRecords_generateRejected_existingRowUpdated() {
        mockTraceOk();
        doThrow(new TaskRejectedException("aiExecutor 已满"))
                .when(summaryService).generate(CLUB, ACT, null);
        ActivitySummary exist = new ActivitySummary();
        exist.setId(1L);
        exist.setActivityId(ACT);
        exist.setRetryCount(2);
        when(summaryMapper.selectOne(any())).thenReturn(exist);

        activityService.closeRecords(CLUB, ACT, null, true);

        ArgumentCaptor<ActivitySummary> cap = ArgumentCaptor.forClass(ActivitySummary.class);
        verify(summaryMapper).updateById(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ActivitySummary.STATUS_FAILED);
        // 被拒不算执行失败，不递增 retryCount（保留重试名额）
        assertThat(cap.getValue().getRetryCount()).isEqualTo(2);
    }

    // ---- cancel（任意非终态 → 10） ----

    @Test
    @DisplayName("cancel 非发起人：403")
    void cancel_notOwner() {
        Activity a = ownerActivity(Activity.STATUS_SIGNUP);
        a.setUserId(OTHER);
        mockOwned(a);

        assertThatThrownBy(() -> activityService.cancel(CLUB, ACT, OWNER, "理由"))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(expectStateForbidden(t).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("cancel 成功：CAS + trace + 全员通知取消理由")
    void cancel_success() {
        Activity a = ownerActivity(Activity.STATUS_SIGNUP);
        mockOwned(a);
        mockTraceOk();
        when(membershipMapper.selectList(any())).thenReturn(List.of(member(1L)));
        when(sysUserMapper.selectById(any())).thenReturn(user("社长"));

        activityService.cancel(CLUB, ACT, OWNER, "场地冲突");

        verify(messageMapper, times(1)).insert(any(Message.class));
        ArgumentCaptor<ActivityTrace> cap = ArgumentCaptor.forClass(ActivityTrace.class);
        verify(activityTraceMapper).insert(cap.capture());
        assertThat(cap.getValue().getAction()).isEqualTo(ActivityTrace.ACTION_CANCEL);
        assertThat(cap.getValue().getDetail()).isEqualTo("场地冲突");
    }

    // ---- 辅助构造 ----

    private ChatMessage msg(Long senderId, boolean lowQuality) {
        ChatMessage m = new ChatMessage();
        m.setActivityId(ACT);
        m.setSenderId(senderId);
        m.setLowQuality(lowQuality);
        return m;
    }

    private Membership member(Long userId) {
        Membership m = new Membership();
        m.setClubId(CLUB);
        m.setUserId(userId);
        m.setStatus(Membership.STATUS_APPROVED);
        return m;
    }

    private SysUser user(String nickname) {
        SysUser u = new SysUser();
        u.setNickname(nickname);
        return u;
    }
}
