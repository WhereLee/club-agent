package com.club.agent.service;

import com.club.agent.dto.RecordScoreDTO;
import com.club.agent.vo.RecordScoreVO;

import java.util.List;

/**
 * 留痕打分服务（块 H）：管理员手动打分 + Java AI 预评（并列展示，双标准之一的质量分）。
 * AI 预评不落库（建议值），最终分由管理员确认后落库。
 */
public interface RecordScoreService {

    /** Java AI 预评（单次线性；不落库，与管理层参考） */
    RecordScoreVO preview(Long clubId, Long activityId, Long userId);

    /** 管理员打分落库（重复打分 1049） */
    void score(Long clubId, Long activityId, Long operatorId, RecordScoreDTO dto);

    /** 已提交留痕者的打分状态（管理层） */
    List<RecordScoreVO> list(Long clubId, Long activityId);
}
