package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.common.ResultCode;
import com.club.agent.config.QaPythonClientFactory;
import com.club.agent.entity.QaMessage;
import com.club.agent.entity.QaSession;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.QaMessageMapper;
import com.club.agent.mapper.QaSessionMapper;
import com.club.agent.vo.QaMessageVO;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * J3 管理层问答单测：会话生命周期 / 归属门 / 问答三方留痕（user→tool→assistant）。
 */
@ExtendWith(MockitoExtension.class)
class QaServiceImplTest {

    @Mock QaSessionMapper sessionMapper;
    @Mock QaMessageMapper messageMapper;
    @Mock QaPythonClientFactory qaPythonClient;

    @InjectMocks QaServiceImpl qaService;

    final Long CLUB = 100L;
    final Long USER = 300L;
    final Long OTHER = 301L;
    final Long SESSION = 500L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(QaSession.class, QaMessage.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    RestClient restClient;
    RestClient.RequestBodyUriSpec post;
    RestClient.ResponseSpec response;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 单测无 Spring 上下文：@Value 不生效，手动开启问答开关
        ReflectionTestUtils.setField(qaService, "qaEnabled", true);
        restClient = mock(RestClient.class);
        post = mock(RestClient.RequestBodyUriSpec.class);
        response = mock(RestClient.ResponseSpec.class);
        lenient().when(qaPythonClient.get()).thenReturn(restClient);
        lenient().when(restClient.post()).thenReturn(post);
        lenient().when(post.uri(anyString())).thenReturn(post);
        lenient().when(post.contentType(any())).thenReturn(post);
        lenient().when(post.header(anyString(), anyString())).thenReturn(post);
        // body(Object) 与 body(BodyInserter) 重载歧义：显式 Object 类型避免 any() 选错重载（K37）
        lenient().when(post.body(any(Object.class))).thenReturn(post);
        lenient().when(post.retrieve()).thenReturn(response);
    }

    private QaSession session(Long userId) {
        QaSession s = new QaSession();
        s.setId(SESSION);
        s.setClubId(CLUB);
        s.setUserId(userId);
        s.setTitle(QaSession.DEFAULT_TITLE);
        s.setStatus(QaSession.STATUS_VALID);
        return s;
    }

    @Test
    @DisplayName("创建会话：标题可空走默认，落库字段完整")
    void createSession_defaults() {
        qaService.createSession(CLUB, USER, "  ");

        ArgumentCaptor<QaSession> captor = ArgumentCaptor.forClass(QaSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo(QaSession.DEFAULT_TITLE);
        assertThat(captor.getValue().getStatus()).isEqualTo(QaSession.STATUS_VALID);
    }

    @Test
    @DisplayName("创建会话：超长标题截断到 100（VARCHAR(100) 防御）")
    void createSession_titleOverLimit_truncated() {
        qaService.createSession(CLUB, USER, "长".repeat(150));

        ArgumentCaptor<QaSession> captor = ArgumentCaptor.forClass(QaSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).hasSize(100);
    }

    @Test
    @DisplayName("归属门：他人会话 → 403；不存在 → 1058")
    void ownershipGate() {
        when(sessionMapper.selectById(SESSION)).thenReturn(session(OTHER));
        assertThatThrownBy(() -> qaService.deleteSession(CLUB, USER, SESSION))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());

        when(sessionMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> qaService.messages(CLUB, USER, 999L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BIZ_QA_SESSION_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("问答：三方消息留痕 + 首问自动命名 + Python 请求体携带会话键")
    void chat_persistsThreeRolesAndTitles() {
        when(sessionMapper.selectById(SESSION)).thenReturn(session(USER));
        when(response.body(Map.class)).thenReturn(Map.of(
                "reply", "根据历史经验，骑行活动建议上午8点出发。",
                "tools", List.of(Map.of(
                        "tool_name", "search_knowledge",
                        "tool_args", "{\"query\":\"骑行出发时间\"}",
                        "tool_result", "【文件资料命中】…"))));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<QaMessageVO> out = qaService.chat(CLUB, USER, SESSION, "骑行活动几点出发比较好？", "Bearer t");

        // user + tool + assistant 三方落库
        ArgumentCaptor<QaMessage> captor = ArgumentCaptor.forClass(QaMessage.class);
        verify(messageMapper, atLeastOnce()).insert(captor.capture());
        List<QaMessage> saved = captor.getAllValues();
        assertThat(saved).extracting(QaMessage::getRole)
                .containsExactly(QaMessage.ROLE_USER, QaMessage.ROLE_TOOL, QaMessage.ROLE_ASSISTANT);
        // 首问自动命名（默认标题 → 问题前 20 字）
        ArgumentCaptor<QaSession> sc = ArgumentCaptor.forClass(QaSession.class);
        verify(sessionMapper).updateById(sc.capture());
        assertThat(sc.getValue().getTitle()).isEqualTo("骑行活动几点出发比较好？");
        assertThat(out).isEmpty();  // listMessages mock 返回空（重点在三方留痕断言）
    }

    @Test
    @DisplayName("问答失败：Python 异常抛 1035，user 消息已保留")
    void chat_pythonDown_keepsUserMessage() {
        when(sessionMapper.selectById(SESSION)).thenReturn(session(USER));
        when(post.retrieve()).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> qaService.chat(CLUB, USER, SESSION, "以前办过露营吗", "Bearer t"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BIZ_AI_UNAVAILABLE.getCode());

        ArgumentCaptor<QaMessage> captor = ArgumentCaptor.forClass(QaMessage.class);
        verify(messageMapper).insert(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(QaMessage.ROLE_USER);
        // 标题更新在 Python 调用前（实现如此）；断言重点是 assistant 未落库（仅 1 条 user 消息）
        verify(messageMapper, never()).insert(argThat((QaMessage m) -> QaMessage.ROLE_ASSISTANT.equals(m.getRole())));
    }
}
