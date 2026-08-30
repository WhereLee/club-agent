package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 经验库条目：业务知识（筹备/强度/住宿/风险）+ 思考角度（thinking_pattern）。
 * 统一沉淀，供对话 Agent 检索复用（D3 起 search_experience 的真实数据源）。
 */
@Data
@TableName("experience_entry")
public class ExperienceEntry {

    public static final String CATEGORY_THINKING_PATTERN = "thinking_pattern";
    public static final String CATEGORY_PREP = "筹备知识";
    public static final String CATEGORY_LESSON = "总结教训";
    public static final String CATEGORY_CONTEXT = "context";

    public static final int STATUS_VALID = 1;
    public static final int STATUS_VOIDED = 0;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 可空 = 全社团通用 */
    private Long clubId;

    private String category;

    private String title;

    private String content;

    /** 思考角度归属的发起人（可空 = 通用） */
    private Long ownerId;

    /** 来源概念（可追溯） */
    private Long sourceConceptId;

    /** 来源活动（活动后自动沉淀，可追溯；可空 = 概念阶段手动沉淀） */
    private Long activityId;

    /** 结构化指标快照（活动后沉淀；可空） */
    private String metrics;

    /** 沉淀人 */
    private Long sourceUserId;

    private Integer status;

    private LocalDateTime createdAt;
}
