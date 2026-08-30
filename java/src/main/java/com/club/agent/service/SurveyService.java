package com.club.agent.service;

import com.club.agent.dto.SurveyPublishDTO;
import com.club.agent.dto.SurveySubmitDTO;
import com.club.agent.vo.SurveyResultVO;
import com.club.agent.vo.SurveyVO;

/**
 * 问卷域（块 B）：发布 / 详情 / 提交 / 结果 / 关闭进入讨论。
 * 状态机不在本类——状态推进（公示中→问卷中→讨论中）统一走 ActivityService（收口）。
 */
public interface SurveyService {

    /** 发布问卷（发起人本人 + 公示中）：建模板与字段（含系统内置"是否感兴趣"）+ 状态推进 + 全员通知 */
    SurveyVO publish(Long clubId, Long activityId, Long userId, SurveyPublishDTO dto);

    /** 问卷详情（本社团成员；含我的提交状态；不含他人答案） */
    SurveyVO detail(Long clubId, Long activityId, Long userId);

    /** 提交问卷（成员；问卷进行中 + 未截止 + 未提交 + 必填校验） */
    void submit(Long clubId, Long activityId, Long userId, SurveySubmitDTO dto);

    /** 问卷结果（管理层；提交总数 + 每题统计：选项题计数 / 文本题列表） */
    SurveyResultVO result(Long clubId, Long activityId);

    /** 结束问卷开启讨论（发起人本人 + 问卷中 → 讨论中；模板关闭） */
    void startDiscuss(Long clubId, Long activityId, Long userId);
}