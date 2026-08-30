package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 概念全量流水：谁在什么时候做了什么（时间线展示与审计）。
 * action：create/save/submit/vote/revote/withdraw/abandon/teacher_approve/teacher_reject/timeout_void/resign_void。
 */
@Data
@TableName("concept_trace")
public class ConceptTrace {

    public static final String ACTION_CREATE = "create";
    public static final String ACTION_SAVE = "save";
    public static final String ACTION_SUBMIT = "submit";
    public static final String ACTION_VOTE = "vote";
    public static final String ACTION_REVOTE = "revote";
    public static final String ACTION_WITHDRAW = "withdraw";
    public static final String ACTION_ABANDON = "abandon";
    public static final String ACTION_TEACHER_APPROVE = "teacher_approve";
    public static final String ACTION_TEACHER_REJECT = "teacher_reject";
    public static final String ACTION_TIMEOUT_VOID = "timeout_void";
    public static final String ACTION_RESIGN_VOID = "resign_void";
    /** 出现拒绝票，进入复议 */
    public static final String ACTION_REVOTE_NEEDED = "revote_needed";
    /** 两票通过，进入待老师批复 */
    public static final String ACTION_TO_TEACHER = "to_teacher";
    /** 复议再次出现拒绝票，概念作废 */
    public static final String ACTION_REVOTE_FAILED = "revote_failed";

    /** AI 起草采纳（人确认后经 ai-draft 端点落表，detail=决策说明/采纳摘要） */
    public static final String ACTION_AI_DRAFT = "ai_draft";

    /** D3 起：提交后异步生成"发起人思路"简析（detail=生成摘要/失败原因） */
    public static final String ACTION_AI_BRIEF = "ai_brief";

    /** D4 起：SKILL.md 落盘（人确认后经 ai/skill 端点写入，detail=skill 名） */
    public static final String ACTION_AI_SKILL = "ai_skill";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long conceptId;

    private Long operatorId;

    private String operatorName;

    private String action;

    /** 理由/备注 */
    private String detail;

    private LocalDateTime createdAt;
}
