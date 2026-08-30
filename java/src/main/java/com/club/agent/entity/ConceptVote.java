package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 概念投票（状态机数据：判断两票齐/复议轮）。
 * 发起人不投票；撤回时本表记录物理删除（审计由 concept_trace 承担）。
 */
@Data
@TableName("concept_vote")
public class ConceptVote {

    public static final int RESULT_APPROVE = 1;
    public static final int RESULT_REJECT = 0;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long conceptId;

    /** 1=首次投票 2=复议 */
    private Integer round;

    private Long voterId;

    /** 1=赞成 0=拒绝 */
    private Integer result;

    /** 必填理由（留痕主体） */
    private String comment;

    private LocalDateTime createdAt;
}
