package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.common.ResultCode;
import com.club.agent.config.PythonClientFactory;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.mapper.ExperienceEntryMapper;
import com.club.agent.service.SummaryAggregateService;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 总结服务单测（Q3 补网，K31-K35 修复区回归）：
 * generate 状态门 / awaiting 分支落 questions / failed 分支 / resume 发起人校验 / upsert 走 update（K32）。
 * PythonClientFactory mock + RestClient 深链 stub，不连 Python。
 */
@ExtendWith(MockitoExtension.class)
class SummaryServiceImplTest {

    @Mock ActivityMapper activityMapper;
    @Mock ActivitySummaryMapper summaryMapper;
    @Mock ExperienceEntryMapper experienceEntryMapper;
    @Mock SummaryAggregateService aggregateService;
    @Mock PythonClientFactory pythonClientFactory;
    // json() 依赖真实序列化（mock 返回 null 导致 questions/answers 落空），手动注入真实 ObjectMapper

    @InjectMocks SummaryServiceImpl summaryService;

    final Long CLUB = 100L;
    final Long ACT = 200L;
    final Long OWNER = 300L;
    final Long OTHER = 301L;

    RestClient restClient;
    RestClient.RequestBodyUriSpec post;
    RestClient.ResponseSpec response;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(Activity.class, ActivitySummary.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        restClient = org.mockito.Mockito.mock(RestClient.class);
        post = org.mockito.Mockito.mock(RestClient.RequestBodyUriSpec.class);
        response = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
        lenient().when(pythonClientFactory.get()).thenReturn(restClient);
        lenient().when(restClient.post()).thenReturn(post);
        lenient().when(post.uri(anyString())).thenReturn(post);
        lenient().when(post.contentType(any())).thenReturn(post);
        // body(Object) 与 body(BodyInserter) 重载歧义：显式 Object 类型避免 any() 选错重载
        lenient().when(post.body(any(Object.class))).thenReturn(post);
        lenient().when(post.retrieve()).thenReturn(response);
        ReflectionTestUtils.setField(summaryService, "objectMapper", new ObjectMapper());
    }

    private Activity activity(int status) {
        Activity a = new Activity();
        a.setId(ACT);
        a.setClubId(CLUB);
        a.setUserId(OWNER);
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("generate 状态门：仅总结中(8)/已归档(9) 可生成，其他状态直接跳过")
    void generate_stateGate_skips() {
        when(activityMapper.selectById(ACT)).thenReturn(activity(Activity.STATUS_EXECUTING));

        summaryService.generate(CLUB, ACT, null);

        verify(summaryMapper, never()).insert(any(ActivitySummary.class));
        verify(pythonClientFactory, never()).get();
    }

    @Test
    @DisplayName("generate 状态 8：插 pending 行并调 Python；返回 awaiting 时落 questions")
    void generate_awaiting_savesQuestions() {
        when(activityMapper.selectById(ACT)).thenReturn(activity(Activity.STATUS_SUMMARIZING));
        when(summaryMapper.selectOne(any())).thenReturn(null);
        when(aggregateService.aggregate(any(), any())).thenReturn(Map.of("metrics", Map.of()));
        Map<String, Object> resp = Map.of(
                "status", ActivitySummary.STATUS_AWAITING,
                "questions", List.of(Map.of("id", "q1", "question", "活动时间？")));
        when(response.body(Map.class)).thenReturn(resp);

        summaryService.generate(CLUB, ACT, null);

        // 注：insert/updateById 传的是同一对象引用（后续 setStatus 会改），只验证调用发生 + 最终状态
        verify(summaryMapper).insert(any(ActivitySummary.class));
        ArgumentCaptor<ActivitySummary> cap = ArgumentCaptor.forClass(ActivitySummary.class);
        verify(summaryMapper).updateById(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ActivitySummary.STATUS_AWAITING);
        assertThat(cap.getValue().getQuestions()).isNotNull();
        assertThat(cap.getValue().getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("generate 已存在行（K32 回归）：走 updateById 而非 insert（主键冲突修复区）")
    void generate_existingRow_updates() {
        when(activityMapper.selectById(ACT)).thenReturn(activity(Activity.STATUS_SUMMARIZING));
        ActivitySummary exist = new ActivitySummary();
        exist.setId(1L);
        exist.setActivityId(ACT);
        exist.setRetryCount(2);
        when(summaryMapper.selectOne(any())).thenReturn(exist);
        when(aggregateService.aggregate(any(), any())).thenReturn(Map.of());
        Map<String, Object> resp = Map.of(
                "status", ActivitySummary.STATUS_SUCCESS,
                "report", Map.of("metrics", Map.of(), "report_text", "报告"));
        when(response.body(Map.class)).thenReturn(resp);

        summaryService.generate(CLUB, ACT, null);

        verify(summaryMapper, never()).insert(any(ActivitySummary.class));
        // upsert 置 pending + 最终落库各一次；断言最终状态
        ArgumentCaptor<ActivitySummary> cap = ArgumentCaptor.forClass(ActivitySummary.class);
        verify(summaryMapper, times(2)).updateById(cap.capture());
        ActivitySummary finalState = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(ActivitySummary.STATUS_SUCCESS);
        assertThat(finalState.getRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("generate Python 异常：置 failed 待调度重试")
    void generate_pythonError_marksFailed() {
        when(activityMapper.selectById(ACT)).thenReturn(activity(Activity.STATUS_SUMMARIZING));
        when(summaryMapper.selectOne(any())).thenReturn(null);
        when(aggregateService.aggregate(any(), any())).thenReturn(Map.of());
        when(response.body(Map.class)).thenThrow(new RuntimeException("Python 连接失败"));

        summaryService.generate(CLUB, ACT, null);

        ArgumentCaptor<ActivitySummary> cap = ArgumentCaptor.forClass(ActivitySummary.class);
        verify(summaryMapper).updateById(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ActivitySummary.STATUS_FAILED);
    }

    @Test
    @DisplayName("resume 非发起人：403")
    void resume_notOwner_forbidden() {
        when(activityMapper.selectById(ACT)).thenReturn(activity(Activity.STATUS_SUMMARIZING));

        assertThatThrownBy(() -> summaryService.resume(CLUB, ACT, OTHER, Map.of("q1", "答")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("resume 非 awaiting 状态：拒绝恢复")
    void resume_notAwaiting_rejected() {
        when(activityMapper.selectById(ACT)).thenReturn(activity(Activity.STATUS_SUMMARIZING));
        ActivitySummary s = new ActivitySummary();
        s.setStatus(ActivitySummary.STATUS_FAILED);
        when(summaryMapper.selectOne(any())).thenReturn(s);

        assertThatThrownBy(() -> summaryService.resume(CLUB, ACT, OWNER, Map.of("q1", "答")))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("resume 成功：落 answers、置 success、存报告")
    void resume_success() {
        when(activityMapper.selectById(ACT)).thenReturn(activity(Activity.STATUS_SUMMARIZING));
        ActivitySummary s = new ActivitySummary();
        s.setId(1L);
        s.setStatus(ActivitySummary.STATUS_AWAITING);
        when(summaryMapper.selectOne(any())).thenReturn(s);
        Map<String, Object> resp = Map.of("report", Map.of("metrics", Map.of(), "report_text", "恢复后报告"));
        when(response.body(Map.class)).thenReturn(resp);

        summaryService.resume(CLUB, ACT, OWNER, Map.of("q1", "补充回答"));

        ArgumentCaptor<ActivitySummary> cap = ArgumentCaptor.forClass(ActivitySummary.class);
        verify(summaryMapper).updateById(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ActivitySummary.STATUS_SUCCESS);
        assertThat(cap.getValue().getAnswers()).contains("补充回答");
        assertThat(cap.getValue().getGeneratedAt()).isNotNull();
    }
}
