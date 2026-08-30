package com.club.agent.service;

import java.util.Map;

/**
 * 活动总结数据聚合（活动后阶段 I2）：
 * 报名/签到/留痕/奖励/讨论 → 结构化指标（确定性强，Java 计算），
 * 作为总结 Agent（Python 子图）的输入；AI 只做文字总结与经验提炼。
 */
public interface SummaryAggregateService {

    /**
     * 聚合活动后总结输入。
     * 返回结构：
     * {activity: {id, content, plannedTime, plannedLocation, creatorName},
     *  signup: {total, participate, notParticipate, onlineAssist, notInterested},
     *  attendance: {expected, present},
     *  record: {submitted, coverage, avgScore, avgAiScore, missing: [昵称...]},
     *  reward: {levelDist: {等级: 人数}, adoptedSuggestions, topScore},
     *  discussion: {messageCount, qualityRate, highFreqCount}}
     */
    Map<String, Object> aggregate(Long clubId, Long activityId);
}