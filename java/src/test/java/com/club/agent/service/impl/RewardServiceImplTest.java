package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityDiscussionSummary;
import com.club.agent.entity.ActivityRecordScore;
import com.club.agent.entity.ActivitySuggestion;
import com.club.agent.entity.Membership;
import com.club.agent.entity.ScoreConfig;
import com.club.agent.entity.ScoreLevel;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityDiscussionSummaryMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivityRecordScoreMapper;
import com.club.agent.mapper.ActivitySuggestionMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.ScoreConfigMapper;
import com.club.agent.mapper.ScoreLevelMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.vo.RewardVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 奖励统计（块 H）白盒单测：
 * 双标准聚合（频率分 + 建议分 + 留痕分）、等级区间匹配、总分降序、配置缺失兜底。
 */
@ExtendWith(MockitoExtension.class)
class RewardServiceImplTest {

    @Mock ActivityMapper activityMapper;
    @Mock MembershipMapper membershipMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock ActivityDiscussionSummaryMapper summaryMapper;
    @Mock ActivitySuggestionMapper suggestionMapper;
    @Mock ActivityRecordScoreMapper scoreMapper;
    @Mock ScoreConfigMapper configMapper;
    @Mock ScoreLevelMapper levelMapper;

    @InjectMocks RewardServiceImpl rewardService;

    final Long CLUB = 100L;
    final Long ACT = 200L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(Activity.class, ScoreConfig.class, ActivityDiscussionSummary.class,
                ActivitySuggestion.class, ActivityRecordScore.class, ScoreLevel.class, Membership.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    private void mockActivity() {
        Activity a = new Activity();
        a.setId(ACT);
        a.setClubId(CLUB);
        when(activityMapper.selectById(ACT)).thenReturn(a);
    }

    private ScoreConfig cfg(String key, int value) {
        ScoreConfig c = new ScoreConfig();
        c.setCfgKey(key);
        c.setCfgValue(value);
        return c;
    }

    private ScoreLevel level(String name, int min, int max) {
        ScoreLevel l = new ScoreLevel();
        l.setLevelName(name);
        l.setMinScore(min);
        l.setMaxScore(max);
        return l;
    }

    private Membership member(Long userId) {
        Membership m = new Membership();
        m.setClubId(CLUB);
        m.setUserId(userId);
        m.setStatus(Membership.STATUS_APPROVED);
        return m;
    }

    @Test
    @DisplayName("活动不存在：1036")
    void rewards_activityNotFound() {
        when(activityMapper.selectById(ACT)).thenReturn(null);

        assertThatThrownBy(() -> rewardService.rewards(CLUB, ACT))
                .isInstanceOf(BizException.class)
                .satisfies(t -> assertThat(((BizException) t).getCode())
                        .isEqualTo(ResultCode.BIZ_ACTIVITY_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("完整聚合：频率分 + 建议采纳分 + 留痕分 + 等级 + 降序")
    void rewards_fullCalculation() {
        mockActivity();
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(cfg(ScoreConfig.KEY_FREQ, 5), cfg(ScoreConfig.KEY_SUGGESTION, 10)));
        // A 高频；B/C 非高频
        ActivityDiscussionSummary sA = new ActivityDiscussionSummary();
        sA.setUserId(1L);
        sA.setHighFreq(true);
        when(summaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sA));
        // A 2 条采纳建议（mock 模拟 SQL 已按 adopted=true 过滤；未采纳的不在返回集）
        ActivitySuggestion ad1 = sug(1L, true), ad2 = sug(1L, true);
        when(suggestionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ad1, ad2));
        // A 留痕 88；B 留痕 60；C 无
        ActivityRecordScore scA = score(1L, 88), scB = score(2L, 60);
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(scA, scB));
        when(levelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                level("优秀", 80, 200), level("良好", 60, 79), level("待提升", 0, 59)));
        when(membershipMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(member(1L), member(2L), member(3L)));
        when(sysUserMapper.selectById(1L)).thenReturn(user("A"));
        when(sysUserMapper.selectById(2L)).thenReturn(user("B"));
        when(sysUserMapper.selectById(3L)).thenReturn(user("C"));

        List<RewardVO> list = rewardService.rewards(CLUB, ACT);

        assertThat(list).hasSize(3);
        RewardVO vA = list.get(0);
        assertThat(vA.getUserId()).isEqualTo(1L);
        assertThat(vA.getFreqScore()).isEqualTo(5);
        assertThat(vA.getSuggestionScore()).isEqualTo(20);
        assertThat(vA.getRecordScore()).isEqualTo(88);
        assertThat(vA.getTotalScore()).isEqualTo(113);
        assertThat(vA.getLevelName()).isEqualTo("优秀");
        RewardVO vB = list.get(1);
        assertThat(vB.getTotalScore()).isEqualTo(60);
        assertThat(vB.getFreqScore()).isZero();
        assertThat(vB.getSuggestionScore()).isZero();
        assertThat(vB.getLevelName()).isEqualTo("良好");
        RewardVO vC = list.get(2);
        assertThat(vC.getTotalScore()).isZero();
        assertThat(vC.getLevelName()).isEqualTo("待提升");
    }

    @Test
    @DisplayName("未采纳建议不计分：建议分恒为 0")
    void rewards_unadoptedNotCounted() {
        mockActivity();
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(cfg(ScoreConfig.KEY_SUGGESTION, 10)));
        when(summaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        // 未采纳建议（mock 模拟 SQL 已过滤：adopted=true 查询返回空）
        when(suggestionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(levelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(membershipMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member(1L)));
        when(sysUserMapper.selectById(1L)).thenReturn(user("A"));

        List<RewardVO> list = rewardService.rewards(CLUB, ACT);

        assertThat(list.get(0).getSuggestionScore()).isZero();
        assertThat(list.get(0).getLevelName()).isEqualTo("未定级");
    }

    @Test
    @DisplayName("配置缺失兜底：频率/建议分值默认 0，成员仍全量列出")
    void rewards_configMissing() {
        mockActivity();
        when(configMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(summaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(suggestionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(levelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(membershipMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(member(1L), member(2L)));
        when(sysUserMapper.selectById(1L)).thenReturn(user("A"));
        when(sysUserMapper.selectById(2L)).thenReturn(user("B"));

        List<RewardVO> list = rewardService.rewards(CLUB, ACT);

        assertThat(list).hasSize(2);
        assertThat(list).allSatisfy(v -> {
            assertThat(v.getFreqScore()).isZero();
            assertThat(v.getSuggestionScore()).isZero();
        });
    }

    @Test
    @DisplayName("等级匹配按 min desc 取首个命中区间")
    void rewards_levelFirstMatch() {
        // 注意：良好(60,89) 与 合格(60,79) min 相同，真实 SQL 排序在 min 相等时不保证顺序；
        // 此用例依赖配置侧约束——score_level 区间不允许重叠（min 相同即重叠），仅验证"取首个命中"语义
        mockActivity();
        when(configMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(summaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(suggestionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        ActivityRecordScore sc = score(1L, 75);
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sc));
        when(levelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                level("良好", 60, 89), level("合格", 60, 79)));
        when(membershipMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member(1L)));
        when(sysUserMapper.selectById(1L)).thenReturn(user("A"));

        List<RewardVO> list = rewardService.rewards(CLUB, ACT);

        assertThat(list.get(0).getLevelName()).isEqualTo("良好");
    }

    private ActivitySuggestion sug(Long senderId, boolean adopted) {
        ActivitySuggestion s = new ActivitySuggestion();
        s.setActivityId(ACT);
        s.setSenderId(senderId);
        s.setAdopted(adopted);
        return s;
    }

    private ActivityRecordScore score(Long userId, int score) {
        ActivityRecordScore s = new ActivityRecordScore();
        s.setActivityId(ACT);
        s.setUserId(userId);
        s.setScore(score);
        return s;
    }

    private SysUser user(String nickname) {
        SysUser u = new SysUser();
        u.setNickname(nickname);
        return u;
    }
}
