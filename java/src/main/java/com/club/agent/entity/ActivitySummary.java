package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动总结（活动后阶段）：一活动一份（uk）。
 * - 生成时机：进入总结中(8) 自动触发；失败定时重试 + 发起人手动重生成兜底
 * - status: pending(生成中) / awaiting(待发起人确认问题) / success(已生成) / failed(生成失败)
 * - report: {metrics: 结构化指标, report_text: AI 总结文字}；questions/answers 为回问闭环
 */
@Data
@TableName("activity_summary")
public class ActivitySummary {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_AWAITING = "awaiting";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    public static final int MAX_RETRY = 3;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    private String status;

    /** {metrics: {...}, report_text: "..."} */
    private String report;

    /** 待确认问题清单 JSON */
    private String questions;

    /** 发起人回答 JSON */
    private String answers;

    private Integer retryCount;

    private Long generatedBy;

    private LocalDateTime generatedAt;

    private LocalDateTime updatedAt;
}
