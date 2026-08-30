package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityChatMember;
import com.club.agent.entity.ChatMessage;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityChatMemberMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ChatMessageMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.vo.ChatMessageVO;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 讨论群消息发送（块 D）白盒单测：
 * 质量预处理（word_count + 低质量标记，供文件 Agent 消费）、讨论只读校验、广播容错。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock ActivityMapper activityMapper;
    @Mock ChatMessageMapper chatMessageMapper;
    @Mock ActivityChatMemberMapper chatMemberMapper;
    @Mock FormTemplateMapper formTemplateMapper;
    @Mock FormFieldMapper formFieldMapper;
    @Mock FormSubmissionMapper formSubmissionMapper;
    @Mock FormAnswerMapper formAnswerMapper;
    @Mock MembershipMapper membershipMapper;
    @Mock RbacRoleMapper rbacRoleMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock MessageMapper messageMapper;
    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks ChatServiceImpl chatService;

    final Long ACT = 200L;
    final Long USER = 300L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(Activity.class, ChatMessage.class, ActivityChatMember.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    @BeforeEach
    void setUp() {
        // @Value 字段纯 Mockito 不注入：低质量阈值默认 10 字
        ReflectionTestUtils.setField(chatService, "lowQualityMinWords", 10);
    }

    private Activity discussing() {
        Activity a = new Activity();
        a.setId(ACT);
        a.setClubId(100L);
        a.setStatus(Activity.STATUS_DISCUSSING);
        return a;
    }

    private void mockMember() {
        when(chatMemberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
    }

    @Test
    @DisplayName("send 正常消息：wordCount 去空白计数 + 高质量标记")
    void send_normal() {
        mockMember();
        when(activityMapper.selectById(ACT)).thenReturn(discussing());
        when(sysUserMapper.selectById(USER)).thenReturn(user("张三"));

        chatService.send(ACT, USER, "  这次拉练建议路线从东门出发，全程 75 公里 ");

        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(cap.capture());
        ChatMessage m = cap.getValue();
        assertThat(m.getContent()).isEqualTo("这次拉练建议路线从东门出发，全程 75 公里");
        assertThat(m.getWordCount()).isEqualTo(20);
        assertThat(m.getLowQuality()).isFalse();
    }

    @Test
    @DisplayName("send 短回复（<10 字）：低质量标记")
    void send_short_lowQuality() {
        mockMember();
        when(activityMapper.selectById(ACT)).thenReturn(discussing());
        when(sysUserMapper.selectById(USER)).thenReturn(user("张三"));

        chatService.send(ACT, USER, "同意");

        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(cap.capture());
        assertThat(cap.getValue().getWordCount()).isEqualTo(2);
        assertThat(cap.getValue().getLowQuality()).isTrue();
    }

    @Test
    @DisplayName("send 非名单成员：1042 拒绝")
    void send_notMember() {
        when(chatMemberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> chatService.send(ACT, USER, "hello"))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(((BizException) t).getCode())
                        .isEqualTo(ResultCode.BIZ_CHAT_FORBIDDEN.getCode()));
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    @DisplayName("send 活动不存在：1036")
    void send_activityNotFound() {
        mockMember();
        when(activityMapper.selectById(ACT)).thenReturn(null);

        assertThatThrownBy(() -> chatService.send(ACT, USER, "hello"))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(((BizException) t).getCode())
                        .isEqualTo(ResultCode.BIZ_ACTIVITY_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("send 讨论已关闭（closedAt 非空）：1037 只读")
    void send_discussionClosed() {
        mockMember();
        Activity a = discussing();
        a.setDiscussionClosedAt(LocalDateTime.now());
        when(activityMapper.selectById(ACT)).thenReturn(a);

        assertThatThrownBy(() -> chatService.send(ACT, USER, "hello"))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(((BizException) t).getCode())
                        .isEqualTo(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("send 活动已发布（非讨论中）：1037 只读")
    void send_notDiscussing() {
        mockMember();
        Activity a = discussing();
        a.setStatus(Activity.STATUS_PUBLISHED);
        when(activityMapper.selectById(ACT)).thenReturn(a);

        assertThatThrownBy(() -> chatService.send(ACT, USER, "hello"))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(((BizException) t).getCode())
                        .isEqualTo(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("send 空内容：400")
    void send_emptyContent() {
        mockMember();
        when(activityMapper.selectById(ACT)).thenReturn(discussing());

        assertThatThrownBy(() -> chatService.send(ACT, USER, "   "))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(((BizException) t).getCode())
                        .isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("send 超长内容（>2000）：400")
    void send_tooLong() {
        mockMember();
        when(activityMapper.selectById(ACT)).thenReturn(discussing());

        assertThatThrownBy(() -> chatService.send(ACT, USER, "a".repeat(2001)))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(((BizException) t).getCode())
                        .isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("send 广播失败不阻断落库（历史拉取兜底）")
    void send_broadcastFailure_doesNotBlock() {
        mockMember();
        when(activityMapper.selectById(ACT)).thenReturn(discussing());
        when(sysUserMapper.selectById(USER)).thenReturn(user("张三"));
        doThrow(new RuntimeException("ws down")).when(messagingTemplate)
                .convertAndSend(anyString(), any(Object.class));

        ChatMessageVO vo = chatService.send(ACT, USER, "这是一条足够长的正常讨论消息内容");

        assertThat(vo.getContent()).isEqualTo("这是一条足够长的正常讨论消息内容");
        verify(chatMessageMapper).insert(any(ChatMessage.class));
    }

    private SysUser user(String nickname) {
        SysUser u = new SysUser();
        u.setNickname(nickname);
        return u;
    }
}
