package com.club.agent.service;

import com.club.agent.vo.RewardVO;

import java.util.List;

/**
 * 奖励统计服务（块 H）：双标准打分汇总。
 * 频率标准（高频讨论参与分） + 质量标准（建议采纳分 + 留痕分） → 总分 → 等级区间。
 */
public interface RewardService {

    /** 奖励统计（管理层：全员总分 + 等级） */
    List<RewardVO> rewards(Long clubId, Long activityId);
}
