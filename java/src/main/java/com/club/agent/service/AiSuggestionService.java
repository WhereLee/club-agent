package com.club.agent.service;

import com.club.agent.vo.SuggestionVO;

import java.util.List;

/**
 * 讨论建议服务（块 H）：Java AI（Spring AI 单次线性）从高质量消息提炼候选建议，
 * 发起人采纳后计质量分（计分链：messageId → 建议人）。
 */
public interface AiSuggestionService {

    /** AI 提炼建议候选（讨论关闭后；幂等：已提炼直接返回） */
    List<SuggestionVO> extract(Long clubId, Long activityId);

    /** 采纳建议（重复采纳 1048） */
    void adopt(Long clubId, Long activityId, Long userId, Long suggestionId);

    /** 建议列表（管理层） */
    List<SuggestionVO> list(Long clubId, Long activityId);
}
