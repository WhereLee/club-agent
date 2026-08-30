package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.club.agent.service.RewardService;
import com.club.agent.vo.RewardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 奖励统计服务实现（块 H）：
 * - 频率标准：讨论快照 is_high_freq 成员 → freq_score 分（维护高频讨论者权益）
 * - 质量标准：采纳建议每条 + suggestion_score 分；留痕最终分 record_score 直接计入
 * - 等级：score_level 区间表驱动（优秀/良好/合格/待提升）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {

    private final ActivityMapper activityMapper;
    private final MembershipMapper membershipMapper;
    private final SysUserMapper sysUserMapper;
    private final ActivityDiscussionSummaryMapper summaryMapper;
    private final ActivitySuggestionMapper suggestionMapper;
    private final ActivityRecordScoreMapper scoreMapper;
    private final ScoreConfigMapper configMapper;
    private final ScoreLevelMapper levelMapper;

    @Override
    public List<RewardVO> rewards(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        // 分值配置（默认 0 兜底）
        Map<String, Integer> cfg = configMapper.selectList(new LambdaQueryWrapper<ScoreConfig>()
                        .eq(ScoreConfig::getClubId, clubId))
                .stream().collect(Collectors.toMap(ScoreConfig::getCfgKey,
                        c -> c.getCfgValue() == null ? 0 : c.getCfgValue(), (x, y) -> x));
        int suggestionScore = cfg.getOrDefault(ScoreConfig.KEY_SUGGESTION, 0);
        int freqScore = cfg.getOrDefault(ScoreConfig.KEY_FREQ, 0);
        // 高频讨论者（频率标准）
        Map<Long, ActivityDiscussionSummary> hf = summaryMapper.selectList(new LambdaQueryWrapper<ActivityDiscussionSummary>()
                        .eq(ActivityDiscussionSummary::getActivityId, activityId)
                        .eq(ActivityDiscussionSummary::getHighFreq, true))
                .stream().collect(Collectors.toMap(ActivityDiscussionSummary::getUserId, Function.identity(), (x, y) -> x));
        // 建议采纳数（质量标准）
        Map<Long, Long> adopted = suggestionMapper.selectList(new LambdaQueryWrapper<ActivitySuggestion>()
                        .eq(ActivitySuggestion::getActivityId, activityId)
                        .eq(ActivitySuggestion::getAdopted, true))
                .stream().collect(Collectors.groupingBy(ActivitySuggestion::getSenderId, Collectors.counting()));
        // 留痕分（质量标准）
        Map<Long, ActivityRecordScore> scores = scoreMapper.selectList(new LambdaQueryWrapper<ActivityRecordScore>()
                        .eq(ActivityRecordScore::getActivityId, activityId))
                .stream().collect(Collectors.toMap(ActivityRecordScore::getUserId, Function.identity(), (x, y) -> x));
        // 等级区间（min desc 匹配）
        List<ScoreLevel> levels = levelMapper.selectList(new LambdaQueryWrapper<ScoreLevel>()
                .eq(ScoreLevel::getClubId, clubId)
                .orderByDesc(ScoreLevel::getMinScore));
        List<RewardVO> list = new ArrayList<>();
        for (Membership m : membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED))) {
            RewardVO vo = new RewardVO();
            vo.setUserId(m.getUserId());
            SysUser u = sysUserMapper.selectById(m.getUserId());
            vo.setNickname(u == null ? "未知" : u.getNickname());
            vo.setFreqScore(hf.containsKey(m.getUserId()) ? freqScore : 0);
            vo.setSuggestionScore((int) (adopted.getOrDefault(m.getUserId(), 0L) * suggestionScore));
            ActivityRecordScore rs = scores.get(m.getUserId());
            vo.setRecordScore(rs == null ? 0 : rs.getScore());
            int total = vo.getFreqScore() + vo.getSuggestionScore() + vo.getRecordScore();
            vo.setTotalScore(total);
            vo.setLevelName(levelOf(levels, total));
            list.add(vo);
        }
        list.sort(Comparator.comparingInt(RewardVO::getTotalScore).reversed());
        return list;
    }

    private String levelOf(List<ScoreLevel> levels, int total) {
        for (ScoreLevel l : levels) {
            if (total >= l.getMinScore() && total <= l.getMaxScore()) {
                return l.getLevelName();
            }
        }
        return "未定级";
    }
}
