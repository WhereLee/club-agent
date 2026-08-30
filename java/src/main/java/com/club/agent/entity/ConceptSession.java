package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 概念：活动的酝酿阶段（仅管理层可见/参与，老师批复后转活动）。
 * status：1=起草中 2=已提交待审 3=复议中 4=待老师批复 5=已通过 6=已作废。
 * 唯一性：一个社团同一时间最多一个活跃概念（status in 1-4，数据库部分唯一索引兜底）。
 * version：乐观锁，防草稿并发编辑覆盖（saveDraft 冲突提示）。
 */
@Data
@TableName("concept_session")
public class ConceptSession {

    public static final int STATUS_DRAFTING = 1;
    public static final int STATUS_SUBMITTED = 2;
    public static final int STATUS_REVOTING = 3;
    public static final int STATUS_TEACHER_REVIEW = 4;
    public static final int STATUS_APPROVED = 5;
    public static final int STATUS_VOIDED = 6;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long clubId;

    /** 发起者（该社团管理层） */
    private Long userId;

    /** 1=起草中 2=已提交待审 3=复议中 4=待老师批复 5=已通过 6=已作废 */
    private Integer status;

    /** 发起理由（必填；LangGraph AI 起草会话的输入入口） */
    private String reason;

    private String plannedTime;

    private String plannedLocation;

    /** 活动简述 */
    private String content;

    /** D3 起：提交时异步生成的"发起人思路"简析（AI 冻结，投票人/老师详情页可见） */
    private String aiBrief;

    private LocalDateTime submittedAt;

    /** 当前阶段截止时间（提交=提交+36h；进入待老师批复=进入+36h） */
    private LocalDateTime deadline;

    /** 乐观锁（草稿并发编辑覆盖防护；insert 时由 DB DEFAULT 0 兜底） */
    @Version
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
