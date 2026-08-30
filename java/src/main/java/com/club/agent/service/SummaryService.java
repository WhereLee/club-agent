package com.club.agent.service;

import com.club.agent.vo.SummaryVO;

import java.util.Map;

/**
 * 活动总结服务（活动后阶段）：
 * - 进入总结中(8) 自动触发生成（@Async，不阻塞状态推进）；失败定时重试 + 发起人手动重生成兜底
 * - 结构化指标 Java 聚合（确定性强），AI 只做文字总结与经验提炼（Python 子图）
 * - 回问闭环：子图产出待确认问题 → 前端展示 → 发起人回答 → resume 恢复生成
 */
public interface SummaryService {

    /** 生成活动总结（系统自动触发 userId=null；手动重生成 userId=发起人） */
    void generate(Long clubId, Long activityId, Long userId);

    /** 提交待确认问题回答后恢复生成（发起人） */
    void resume(Long clubId, Long activityId, Long userId, Map<String, String> answers);

    /** 总结详情（管理层视图） */
    SummaryVO detail(Long clubId, Long activityId);
}