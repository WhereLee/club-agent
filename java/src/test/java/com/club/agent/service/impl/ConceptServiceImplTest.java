package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.ConceptVoteDTO;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ConceptSession;
import com.club.agent.entity.ConceptTrace;
import com.club.agent.entity.ConceptVote;
import com.club.agent.entity.Membership;
import com.club.agent.entity.Message;
import com.club.agent.entity.RbacRole;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ClubMapper;
import com.club.agent.mapper.ConceptSessionMapper;
import com.club.agent.mapper.ConceptTraceMapper;
import com.club.agent.mapper.ConceptVoteMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ActivityOwnership;
import com.club.agent.service.ActivityService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 概念审批子流程单测（Q3 补网，K12-K14 修复区回归）：
 * vote 收官推进 / 双投拦截 / 复议拒绝作废 / 超时扫描作废。
 */
@ExtendWith(MockitoExtension.class)
class ConceptServiceImplTest {

    @Mock ConceptSessionMapper conceptSessionMapper;
    @Mock ConceptTraceMapper conceptTraceMapper;
    @Mock ConceptVoteMapper conceptVoteMapper;
    @Mock ClubMapper clubMapper;
    @Mock MembershipMapper membershipMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock RbacRoleMapper rbacRoleMapper;
    @Mock MessageMapper messageMapper;
    @Mock ActivityService activityService;
    @Mock ActivityMapper activityMapper;
    @Mock ActivityOwnership ownership;

    @InjectMocks ConceptServiceImpl conceptService;

    final Long CLUB = 100L;
    final Long CONCEPT = 200L;
    final Long REQUESTER = 300L;
    final Long MANAGER_A = 301L;
    final Long MANAGER_B = 302L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(ConceptSession.class, ConceptVote.class, Membership.class, RbacRole.class,
                Message.class, SysUser.class, Activity.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    private ConceptSession session(int status) {
        ConceptSession s = new ConceptSession();
        s.setId(CONCEPT);
        s.setClubId(CLUB);
        s.setUserId(REQUESTER);
        s.setStatus(status);
        s.setDeadline(LocalDateTime.now().plusHours(1));
        return s;
    }

    /** 管理层 2 人（含发起人 1 人 → 期望票数 1） */
    private void mockManagement(int count) {
        when(rbacRoleMapper.selectList(any())).thenReturn(List.of(role(1L)));
        Membership m = new Membership();
        m.setClubId(CLUB);
        m.setUserId(MANAGER_A);
        m.setRoleId(1L);
        when(membershipMapper.selectList(any())).thenReturn(List.of(m));
    }

    private RbacRole role(Long id) {
        RbacRole r = new RbacRole();
        r.setId(id);
        r.setCode("president");
        return r;
    }

    @Test
    @DisplayName("vote 收官：两票全赞成（含发起人则仅 1 票）→ 进入待老师批复")
    void vote_finalApprove_advanceToTeacherReview() {
        when(conceptSessionMapper.selectOne(any())).thenReturn(session(ConceptSession.STATUS_SUBMITTED));
        when(conceptSessionMapper.selectById(CONCEPT)).thenReturn(session(ConceptSession.STATUS_SUBMITTED)); // vote 末尾 detail()
        when(membershipMapper.selectOne(any())).thenReturn(new Membership()); // 成员身份
        when(conceptVoteMapper.selectCount(any())).thenReturn(0L, 1L, 0L);    // 未投 / cast=1 / 无拒绝
        mockManagement(2);
        when(conceptSessionMapper.update(any(), any())).thenReturn(1);        // advance 成功

        ConceptVoteDTO dto = new ConceptVoteDTO();
        dto.setResult(ConceptVote.RESULT_APPROVE);
        dto.setComment("同意");
        conceptService.vote(CLUB, CONCEPT, MANAGER_A, dto);

        verify(conceptVoteMapper).insert(any(ConceptVote.class));
        verify(conceptSessionMapper).update(eq(null), any());
        verify(conceptTraceMapper, times(2)).insert(any(ConceptTrace.class)); // 投票留痕 + 推进留痕
    }

    @Test
    @DisplayName("vote 双投拦截：同轮已投 → 已投票业务码")
    void vote_duplicate_rejected() {
        when(conceptSessionMapper.selectOne(any())).thenReturn(session(ConceptSession.STATUS_SUBMITTED));
        when(membershipMapper.selectOne(any())).thenReturn(new Membership());
        when(conceptVoteMapper.selectCount(any())).thenReturn(1L);

        ConceptVoteDTO dto = new ConceptVoteDTO();
        dto.setResult(ConceptVote.RESULT_APPROVE);
        dto.setComment("同意");

        assertThatThrownBy(() -> conceptService.vote(CLUB, CONCEPT, MANAGER_A, dto))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BIZ_CONCEPT_ALREADY_VOTED.getCode());
        verify(conceptVoteMapper, never()).insert(any(ConceptVote.class));
    }

    @Test
    @DisplayName("vote 复议再次拒绝 → 概念立即作废")
    void vote_revoteReject_voided() {
        when(conceptSessionMapper.selectOne(any())).thenReturn(session(ConceptSession.STATUS_REVOTING));
        when(conceptSessionMapper.selectById(CONCEPT)).thenReturn(session(ConceptSession.STATUS_REVOTING)); // vote 末尾 detail()
        when(membershipMapper.selectOne(any())).thenReturn(new Membership());
        when(conceptVoteMapper.selectCount(any())).thenReturn(0L, 1L, 1L);    // 未投 / cast=1 / 存在拒绝
        mockManagement(2);
        when(conceptSessionMapper.update(any(), any())).thenReturn(1);

        ConceptVoteDTO dto = new ConceptVoteDTO();
        dto.setResult(ConceptVote.RESULT_REJECT);
        dto.setComment("再次拒绝");
        conceptService.vote(CLUB, CONCEPT, MANAGER_A, dto);

        verify(conceptSessionMapper).update(eq(null), any());
        verify(conceptTraceMapper, times(2)).insert(any(ConceptTrace.class)); // 投票留痕 + 作废留痕
        // 作废通知管理层
        verify(messageMapper, org.mockito.Mockito.atLeastOnce()).insert(any(Message.class));
    }

    @Test
    @DisplayName("scanTimeout：超时概念自动作废（K12 回归）")
    void scanTimeout_voidsExpired() {
        ConceptSession expired = session(ConceptSession.STATUS_TEACHER_REVIEW);
        expired.setDeadline(LocalDateTime.now().minusMinutes(1));
        when(conceptSessionMapper.selectList(any())).thenReturn(List.of(expired));
        when(conceptSessionMapper.update(any(), any())).thenReturn(1);
        mockManagement(2);

        conceptService.scanTimeout();

        verify(conceptSessionMapper).update(eq(null), any());
        verify(conceptTraceMapper).insert(any(ConceptTrace.class));
    }
}
