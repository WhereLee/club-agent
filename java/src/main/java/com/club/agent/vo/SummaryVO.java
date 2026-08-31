package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 活动总结详情（管理层视图）：状态 + 结构化指标 + AI 总结 + 待确认问题闭环 */
@Data
public class SummaryVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** pending / awaiting / success / failed */
    private String status;

    /** 结构化指标（Java 聚合，确定性强） */
    private Map<String, Object> metrics;

    /** AI 总结文字 */
    private String reportText;

    /** 待确认问题清单 [{id, question}] */
    private List<Map<String, Object>> questions;

    /** 发起人回答 {questionId: answer} */
    private Map<String, Object> answers;

    /** 本次沉淀的经验条目（来源本活动，可追溯） */
    private List<Map<String, Object>> lessons;

    private Integer retryCount;

    private LocalDateTime generatedAt;
}